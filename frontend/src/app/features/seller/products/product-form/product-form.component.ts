import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, concatMap, EMPTY, filter, finalize, from, take, tap } from 'rxjs';
import { CameraOff, Trash2 } from 'lucide-angular';
import { LucideAngularModule } from 'lucide-angular';
import { ProductService } from '../../../../core/services/product.service';
import { MediaService } from '../../../../core/services/media.service';
import { ToastService } from '../../../../core/services/toast.service';
import { AuthService } from '../../../../core/services/auth.service';
import { MediaUploadComplete } from '../../../../core/models/media.model';
import { FileUploadComponent } from '../../../../shared/components/file-upload/file-upload.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { MediaImageComponent } from '../../../../shared/components/media-image/media-image.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

@Component({
  selector: 'app-product-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    FileUploadComponent,
    LucideAngularModule,
    ConfirmDialogComponent,
    LoadingSpinnerComponent,
    MediaImageComponent,
    SellerPortalShellComponent
  ],
  templateUrl: './product-form.component.html',
  styleUrl: './product-form.component.scss'
})
export class ProductFormComponent implements OnInit {
  fb = inject(FormBuilder);
  route = inject(ActivatedRoute);
  router = inject(Router);
  productService = inject(ProductService);
  mediaService = inject(MediaService);
  toastService = inject(ToastService);
  authService = inject(AuthService);

  productForm!: FormGroup;
  isEditMode = false;
  productId: string | null = null;
  isLoading = false;
  isSubmitting = false;

  uploadedImages: string[] = [];
  isUploading = false;
  uploadProgress = 0;
  uploadProgressLabel = 'UPLOADING...';
  readonly maxUploadSizeBytes = 2 * 1024 * 1024;
  readonly maxImages = 4;
  readonly maxProductNameLength = 150;
  isRemoveImageConfirmOpen = false;
  imageIndexToRemove: number | null = null;

  readonly Trash2Icon = Trash2;
  readonly CameraOffIcon = CameraOff;

  ngOnInit(): void {
    this.initForm();

    this.route.paramMap.subscribe((params) => {
      const id = params.get('id');
      if (id) {
        this.isEditMode = true;
        this.productId = id;
        this.loadProduct(id);
      }
    });
  }

  initForm(): void {
    this.productForm = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(this.maxProductNameLength)]],
      description: ['', [Validators.required, Validators.minLength(10)]],
      price: [null, [Validators.required, Validators.min(0.01)]],
      quantity: [1, [Validators.required, Validators.min(0)]]
    });
  }

  loadProduct(id: string): void {
    this.isLoading = true;
    this.productService.getById(id).subscribe({
      next: (product) => {
        if (product.sellerId !== this.authService.currentUserValue?.id) {
          this.isLoading = false;
          this.toastService.show('You can only edit your own products.', 'warning');
          this.router.navigate(['/seller/products']);
          return;
        }

        this.isLoading = false;
        this.productForm.patchValue({
          name: product.name,
          description: product.description,
          price: product.price,
          quantity: product.quantity
        });
        if (product.imageUrls) {
          this.uploadedImages = [...product.imageUrls];
        }
      },
      error: () => {
        this.isLoading = false;
        this.toastService.show('Failed to load product for editing', 'error');
        this.router.navigate(['/seller/products']);
      }
    });
  }

  onFilesSelected(files: File[]): void {
    if (this.isUploading || files.length === 0) {
      return;
    }

    if (this.remainingImageSlots === 0) {
      this.toastService.show(`You can attach up to ${this.maxImages} images per product.`, 'warning');
      return;
    }

    if (files.length > this.remainingImageSlots) {
      this.toastService.show(
        `You can upload ${this.remainingImageSlots} more ${this.remainingImageSlots === 1 ? 'image' : 'images'} for this product.`,
        'warning'
      );
      return;
    }

    let uploadedCount = 0;
    let failedCount = 0;
    let lastErrorMessage = '';

    this.isUploading = true;
    this.uploadProgress = 0;
    this.uploadProgressLabel = files.length === 1 ? 'UPLOADING IMAGE...' : `UPLOADING 1 OF ${files.length}...`;

    from(files)
      .pipe(
        concatMap((file, index) =>
          this.mediaService.upload(file, this.productId).pipe(
            tap((event) => {
              if (event.kind === 'progress') {
                this.uploadProgressLabel =
                  files.length === 1 ? 'UPLOADING IMAGE...' : `UPLOADING ${index + 1} OF ${files.length}...`;
                this.uploadProgress = Math.round(((index + (event.progress / 100)) / files.length) * 100);
                return;
              }

              uploadedCount += 1;
              this.uploadedImages = [...this.uploadedImages, event.media.url];
              this.uploadProgress = Math.round(((index + 1) / files.length) * 100);
            }),
            filter((event): event is MediaUploadComplete => event.kind === 'complete'),
            take(1),
            catchError((err) => {
              failedCount += 1;
              lastErrorMessage = err.error?.message || 'Failed to upload image';
              return EMPTY;
            })
          )
        ),
        finalize(() => {
          this.isUploading = false;
          this.uploadProgress = 0;
          this.uploadProgressLabel = 'UPLOADING...';

          if (uploadedCount > 0 && failedCount === 0) {
            this.toastService.show(
              `${uploadedCount} ${uploadedCount === 1 ? 'image' : 'images'} uploaded successfully`,
              'success'
            );
            return;
          }

          if (uploadedCount > 0) {
            this.toastService.show(
              `${uploadedCount} ${uploadedCount === 1 ? 'image' : 'images'} uploaded, ${failedCount} failed`,
              'warning'
            );
            return;
          }

          this.toastService.show(
            lastErrorMessage || `Failed to upload ${failedCount} ${failedCount === 1 ? 'image' : 'images'}`,
            'error'
          );
        })
      )
      .subscribe();
  }

  confirmRemoveImage(index: number): void {
    if (this.isUploading) {
      return;
    }

    this.imageIndexToRemove = index;
    this.isRemoveImageConfirmOpen = true;
  }

  removeImage(index: number): void {
    this.uploadedImages = this.uploadedImages.filter((_, imageIndex) => imageIndex !== index);
  }

  onRemoveImageConfirmed(): void {
    if (this.imageIndexToRemove === null) {
      return;
    }

    this.removeImage(this.imageIndexToRemove);
    this.closeRemoveImageDialog();
  }

  onRemoveImageCancelled(): void {
    this.closeRemoveImageDialog();
  }

  onSubmit(): void {
    if (this.isUploading) {
      this.toastService.show('Wait for the image upload to finish before saving the product.', 'warning');
      return;
    }

    if (this.productForm.invalid) {
      this.productForm.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    const productData = {
      ...this.productForm.getRawValue(),
      imageUrls: this.uploadedImages
    };

    const request = this.isEditMode && this.productId
      ? this.productService.update(this.productId, productData)
      : this.productService.create(productData);

    request.subscribe({
      next: () => {
        this.toastService.show(`Product ${this.isEditMode ? 'updated' : 'created'} successfully`, 'success');
        this.router.navigate(['/seller/products']);
      },
      error: (err) => {
        this.isSubmitting = false;
        this.toastService.show(
          err.error?.message || `Failed to ${this.isEditMode ? 'update' : 'create'} product`,
          'error'
        );
      }
    });
  }

  isInvalid(field: string): boolean {
    const control = this.productForm.get(field);
    return !!(control && control.invalid && (control.dirty || control.touched));
  }

  get remainingImageSlots(): number {
    return Math.max(this.maxImages - this.uploadedImages.length, 0);
  }

  get uploadTitle(): string {
    return this.remainingImageSlots === 0 ? 'Image limit reached' : 'Upload product images';
  }

  get uploadDescription(): string {
    if (this.remainingImageSlots === 0) {
      return `This product already has ${this.uploadedImages.length} attached images. Remove one to upload another.`;
    }

    return `Select up to ${this.remainingImageSlots} ${this.remainingImageSlots === 1 ? 'image' : 'images'} at once, 2 MB each.`;
  }

  get uploadButtonLabel(): string {
    if (this.remainingImageSlots === 0) {
      return 'Maximum reached';
    }

    return this.remainingImageSlots === 1 ? 'Choose image' : `Choose up to ${this.remainingImageSlots}`;
  }

  get removeImageMessage(): string {
    return this.isEditMode
      ? 'Remove this image from the product gallery? The file stays in your media library unless you delete it there separately.'
      : 'Remove this image from this draft product gallery?';
  }

  private closeRemoveImageDialog(): void {
    this.imageIndexToRemove = null;
    this.isRemoveImageConfirmOpen = false;
  }
}
