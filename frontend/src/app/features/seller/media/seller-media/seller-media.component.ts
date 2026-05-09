import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MediaService } from '../../../../core/services/media.service';
import { ToastService } from '../../../../core/services/toast.service';
import { FileUploadComponent } from '../../../../shared/components/file-upload/file-upload.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { MediaImageComponent } from '../../../../shared/components/media-image/media-image.component';
import { LucideAngularModule, Image as ImageIcon, Trash2, Copy, CheckCircle2, Search, X, Link2 } from 'lucide-angular';
import { MediaItem, MediaUploadComplete } from '../../../../core/models/media.model';
import { ProductService } from '../../../../core/services/product.service';
import { Product } from '../../../../core/models/product.model';
import { catchError, concatMap, EMPTY, filter, finalize, from, take, tap } from 'rxjs';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

@Component({
  selector: 'app-seller-media',
  standalone: true,
  imports: [CommonModule, FormsModule, FileUploadComponent, LoadingSpinnerComponent, MediaImageComponent, LucideAngularModule, ConfirmDialogComponent, SellerPortalShellComponent],
  templateUrl: './seller-media.component.html',
  styleUrl: './seller-media.component.scss'
})
export class SellerMediaComponent implements OnInit {
  mediaService = inject(MediaService);
  productService = inject(ProductService);
  toastService = inject(ToastService);

  mediaItems: MediaItem[] = [];
  products: Product[] = [];
  librarySearch = '';
  filterProductId = '';
  uploadProductId = '';
  productsLoaded = false;
  isMediaLoading = true;
  isUploading = false;
  uploadProgress = 0;
  uploadProgressLabel = 'UPLOADING...';
  readonly maxUploadSizeBytes = 2 * 1024 * 1024;
  readonly maxBatchUploads = 4;

  readonly ImageIconIcon = ImageIcon;
  readonly Trash2Icon = Trash2;
  readonly CopyIcon = Copy;
  readonly CheckCircle2Icon = CheckCircle2;
  readonly SearchIcon = Search;
  readonly XIcon = X;
  readonly Link2Icon = Link2;

  copiedId: string | null = null;
  assignmentSavingIds = new Set<string>();
  assignmentModalMedia: MediaItem | null = null;
  assignmentSearch = '';
  isDeleteConfirmOpen = false;
  mediaToDelete: MediaItem | null = null;

  ngOnInit() {
    this.loadProducts();
    this.loadMedia();
  }

  onFilesSelected(files: File[]) {
    if (this.isUploading || files.length === 0) {
      return;
    }

    if (files.length > this.maxBatchUploads) {
      this.toastService.show(`You can upload up to ${this.maxBatchUploads} images at a time.`, 'warning');
      return;
    }

    let uploadedCount = 0;
    let failedCount = 0;
    let lastErrorMessage = '';
    let uploadedAssignedMedia = false;

    this.isUploading = true;
    this.uploadProgress = 0;
    this.uploadProgressLabel = files.length === 1 ? 'UPLOADING IMAGE...' : `UPLOADING 1 OF ${files.length}...`;

    from(files)
      .pipe(
        concatMap((file, index) =>
          this.mediaService.upload(file, this.uploadProductId || undefined).pipe(
            tap((event) => {
              if (event.kind === 'progress') {
                this.uploadProgressLabel =
                  files.length === 1 ? 'UPLOADING IMAGE...' : `UPLOADING ${index + 1} OF ${files.length}...`;
                this.uploadProgress = Math.round(((index + (event.progress / 100)) / files.length) * 100);
                return;
              }

              uploadedCount += 1;
              uploadedAssignedMedia = uploadedAssignedMedia || !!event.media.productId;
              if (this.shouldIncludeInCurrentFilter(event.media)) {
                this.mediaItems = [event.media, ...this.mediaItems];
              }
              this.uploadProgress = Math.round(((index + 1) / files.length) * 100);
            }),
            filter((event): event is MediaUploadComplete => event.kind === 'complete'),
            take(1),
            catchError((err) => {
              failedCount += 1;
              lastErrorMessage = err.error?.message || 'Upload failed';
              return EMPTY;
            })
          )
        ),
        finalize(() => {
          if (uploadedAssignedMedia) {
            this.productService.invalidateCache();
          }

          this.isUploading = false;
          this.uploadProgress = 0;
          this.uploadProgressLabel = 'UPLOADING...';

          if (uploadedCount > 0 && failedCount === 0) {
            this.toastService.show(
              `${uploadedCount} ${uploadedCount === 1 ? 'asset' : 'assets'} uploaded to the media library`,
              'success'
            );
            return;
          }

          if (uploadedCount > 0) {
            this.toastService.show(
              `${uploadedCount} ${uploadedCount === 1 ? 'asset' : 'assets'} uploaded, ${failedCount} failed`,
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

  onFilterChange(productId: string) {
    this.filterProductId = productId;
    this.loadMedia();
  }

  onUploadTargetChange(productId: string) {
    this.uploadProductId = productId;
  }

  copyUrl(url: string, mediaId: string) {
    navigator.clipboard.writeText(url).then(() => {
      this.copiedId = mediaId;
      setTimeout(() => this.copiedId = null, 2000);
      this.toastService.show('URL copied to clipboard', 'info');
    }).catch(() => {
      this.toastService.show('Failed to copy URL', 'error');
    });
  }

  confirmDeleteMedia(media: MediaItem): void {
    this.mediaToDelete = media;
    this.isDeleteConfirmOpen = true;
  }

  deleteMedia(media: MediaItem) {
    this.mediaService.delete(media.id).subscribe({
      next: () => {
        this.mediaItems = this.mediaItems.filter((item) => item.id !== media.id);
        if (media.productId) {
          this.productService.invalidateCache();
        }
        this.toastService.show('Asset removed from library', 'success');
      },
      error: (err) => {
        this.toastService.show(err.error?.message || 'Failed to delete asset', 'error');
      }
    });
  }

  onDeleteConfirmed(): void {
    const media = this.mediaToDelete;
    if (!media) {
      return;
    }

    this.deleteMedia(media);
    this.closeDeleteDialog();
  }

  onDeleteCancelled(): void {
    this.closeDeleteDialog();
  }

  updateAssignment(media: MediaItem, productId: string | null) {
    const nextProductId = productId || null;
    const previousProductId = media.productId ?? null;

    if (this.assignmentSavingIds.has(media.id) || previousProductId === nextProductId) {
      return;
    }

    this.assignmentSavingIds.add(media.id);

    this.mediaService.updateAssignment(media.id, nextProductId).pipe(
      finalize(() => this.assignmentSavingIds.delete(media.id))
    ).subscribe({
      next: (updatedMedia) => {
        this.productService.invalidateCache();

        if (this.shouldIncludeInCurrentFilter(updatedMedia)) {
          this.mediaItems = this.mediaItems.map((item) => item.id === updatedMedia.id ? updatedMedia : item);
        } else {
          this.mediaItems = this.mediaItems.filter((item) => item.id !== updatedMedia.id);
        }

        if (this.assignmentModalMedia?.id === updatedMedia.id) {
          this.closeAssignmentModal(true);
        }

        this.toastService.show(
          updatedMedia.productId ? 'Asset linked to product' : 'Asset moved back to unassigned',
          'success'
        );
      },
      error: (err) => {
        this.loadMedia();
        this.toastService.show(err.error?.message || 'Failed to update asset assignment', 'error');
      }
    });
  }

  getProductName(productId?: string | null): string {
    if (!productId) {
      return 'Unassigned';
    }

    if (!this.productsLoaded) {
      return 'Loading assignment...';
    }

    return this.products.find((product) => product.id === productId)?.name || 'Unknown product';
  }

  private shouldIncludeInCurrentFilter(media: MediaItem): boolean {
    return !this.filterProductId || media.productId === this.filterProductId;
  }

  hasProduct(productId?: string | null): boolean {
    return !!productId && this.products.some((product) => product.id === productId);
  }

  openAssignmentModal(media: MediaItem): void {
    if (this.isAssignmentSaving(media.id)) {
      return;
    }

    this.assignmentModalMedia = media;
    this.assignmentSearch = '';
  }

  closeAssignmentModal(force = false): void {
    if (!force && this.assignmentModalMedia && this.isAssignmentSaving(this.assignmentModalMedia.id)) {
      return;
    }

    this.assignmentModalMedia = null;
    this.assignmentSearch = '';
  }

  assignFromModal(productId: string | null | undefined): void {
    if (!this.assignmentModalMedia) {
      return;
    }

    this.updateAssignment(this.assignmentModalMedia, productId ?? null);
  }

  get filteredProducts(): Product[] {
    const search = this.assignmentSearch.trim().toLowerCase();
    if (!search) {
      return this.products;
    }

    return this.products.filter((product) => product.name.toLowerCase().includes(search));
  }

  isAssignmentSaving(mediaId: string): boolean {
    return this.assignmentSavingIds.has(mediaId);
  }

  trackByMediaId(index: number, media: MediaItem): string {
    return media.id;
  }

  get visibleAssetCount(): number {
    return this.visibleMediaItems.length;
  }

  get assignedAssetCount(): number {
    return this.mediaItems.filter((media) => !!media.productId).length;
  }

  get unassignedAssetCount(): number {
    return this.mediaItems.filter((media) => !media.productId).length;
  }

  get deleteMediaMessage(): string {
    if (!this.mediaToDelete) {
      return 'Are you sure you want to permanently delete this image? This action cannot be undone.';
    }

    return this.mediaToDelete.productId
      ? 'Are you sure you want to permanently delete this image? It will disappear from the media library and from the linked product page.'
      : 'Are you sure you want to permanently delete this image? This action cannot be undone.';
  }

  get visibleMediaItems(): MediaItem[] {
    const search = this.librarySearch.trim().toLowerCase();
    if (!search) {
      return this.mediaItems;
    }

    return this.mediaItems.filter((media) => {
      const productName = this.getProductName(media.productId).toLowerCase();
      return media.filename.toLowerCase().includes(search) || productName.includes(search);
    });
  }

  private loadProducts() {
    this.productService.getMyProducts().subscribe({
      next: (products) => {
        this.products = products;
        this.productsLoaded = true;
      },
      error: (err) => {
        this.productsLoaded = true;
        this.toastService.show(err.error?.message || 'Failed to load your products', 'error');
      }
    });
  }

  private loadMedia() {
    this.isMediaLoading = true;
    this.mediaService.getMyMedia(this.filterProductId || undefined).subscribe({
      next: (items) => {
        this.mediaItems = items;
        this.isMediaLoading = false;
      },
      error: (err) => {
        this.isMediaLoading = false;
        this.toastService.show(err.error?.message || 'Failed to load media library', 'error');
      }
    });
  }

  private closeDeleteDialog(): void {
    this.mediaToDelete = null;
    this.isDeleteConfirmOpen = false;
  }
}
