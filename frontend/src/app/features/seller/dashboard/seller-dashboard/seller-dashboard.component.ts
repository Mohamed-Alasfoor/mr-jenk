import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { LucideAngularModule, Trash2 } from 'lucide-angular';
import { catchError, forkJoin, of } from 'rxjs';
import { ProductService } from '../../../../core/services/product.service';
import { MediaService } from '../../../../core/services/media.service';
import { Product } from '../../../../core/models/product.model';
import { MediaItem } from '../../../../core/models/media.model';
import { ToastService } from '../../../../core/services/toast.service';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

@Component({
  selector: 'app-seller-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    LucideAngularModule,
    LoadingSpinnerComponent,
    ConfirmDialogComponent,
    SellerPortalShellComponent
  ],
  templateUrl: './seller-dashboard.component.html',
  styleUrl: './seller-dashboard.component.scss'
})
export class SellerDashboardComponent implements OnInit {
  productService = inject(ProductService);
  mediaService = inject(MediaService);
  toastService = inject(ToastService);

  products: Product[] = [];
  mediaItems: MediaItem[] = [];
  isLoading = true;
  isDeleteConfirmOpen = false;
  productToDelete: Product | null = null;
  readonly Trash2Icon = Trash2;

  ngOnInit(): void {
    forkJoin({
      products: this.productService.getAllBySeller().pipe(
        catchError((err) => {
          this.toastService.show(err.error?.message || 'Failed to load products for the dashboard', 'error');
          return of([] as Product[]);
        })
      ),
      mediaItems: this.mediaService.getMyMedia().pipe(
        catchError((err) => {
          this.toastService.show(err.error?.message || 'Failed to load media metrics', 'error');
          return of([] as MediaItem[]);
        })
      )
    }).subscribe({
      next: ({ products, mediaItems }) => {
        this.products = products;
        this.mediaItems = mediaItems;
        this.isLoading = false;
      }
    });
  }

  get activeListingsCount(): number {
    return this.products.length;
  }

  get totalInventoryUnits(): number {
    return this.products.reduce((sum, product) => sum + product.quantity, 0);
  }

  get totalMediaAssets(): number {
    return this.mediaItems.length;
  }

  get recentProducts(): Product[] {
    return this.products.slice(0, 5);
  }

  get linkedMediaCount(): number {
    return this.mediaItems.filter((item) => !!item.productId).length;
  }

  confirmDelete(product: Product): void {
    this.productToDelete = product;
    this.isDeleteConfirmOpen = true;
  }

  onDeleteConfirmed(): void {
    const product = this.productToDelete;
    if (!product) {
      return;
    }

    this.productService.delete(product.id).subscribe({
      next: () => {
        this.products = this.products.filter((item) => item.id !== product.id);
        this.mediaItems = this.mediaItems.map((item) =>
          item.productId === product.id ? { ...item, productId: null } : item
        );
        this.toastService.show('Product deleted successfully', 'success');
        this.closeDeleteDialog();
      },
      error: (err) => {
        this.toastService.show(err.error?.message || 'Failed to delete product', 'error');
        this.closeDeleteDialog();
      }
    });
  }

  onDeleteCancelled(): void {
    this.closeDeleteDialog();
  }

  private closeDeleteDialog(): void {
    this.productToDelete = null;
    this.isDeleteConfirmOpen = false;
  }
}
