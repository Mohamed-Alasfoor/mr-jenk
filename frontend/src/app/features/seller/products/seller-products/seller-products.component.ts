import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CameraOff, Pencil, Trash2 } from 'lucide-angular';
import { LucideAngularModule } from 'lucide-angular';
import { ProductService } from '../../../../core/services/product.service';
import { Product } from '../../../../core/models/product.model';
import { ToastService } from '../../../../core/services/toast.service';
import { SkeletonComponent } from '../../../../shared/components/skeleton/skeleton.component';
import { EmptyStateComponent } from '../../../../shared/components/empty-state/empty-state.component';
import { ConfirmDialogComponent } from '../../../../shared/components/confirm-dialog/confirm-dialog.component';
import { MediaImageComponent } from '../../../../shared/components/media-image/media-image.component';
import { SellerPortalShellComponent } from '../../../../shared/components/seller-portal-shell/seller-portal-shell.component';

@Component({
  selector: 'app-seller-products',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    LucideAngularModule,
    SkeletonComponent,
    EmptyStateComponent,
    ConfirmDialogComponent,
    MediaImageComponent,
    SellerPortalShellComponent
  ],
  templateUrl: './seller-products.component.html',
  styleUrl: './seller-products.component.scss'
})
export class SellerProductsComponent implements OnInit {
  productService = inject(ProductService);
  toastService = inject(ToastService);

  products: Product[] = [];
  isLoading = true;
  skeletonArray = Array(5).fill(0);

  isConfirmOpen = false;
  productToDelete: string | null = null;

  readonly CameraOffIcon = CameraOff;
  readonly PencilIcon = Pencil;
  readonly Trash2Icon = Trash2;

  get totalInventoryUnits(): number {
    return this.products.reduce((sum, product) => sum + product.quantity, 0);
  }

  get totalInventoryValue(): number {
    return this.products.reduce((sum, product) => sum + (product.price * product.quantity), 0);
  }

  get lowStockCount(): number {
    return this.products.filter((product) => product.quantity > 0 && product.quantity < 5).length;
  }

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    this.isLoading = true;
    this.productService.getAllBySeller().subscribe({
      next: (data) => {
        this.products = data;
        this.isLoading = false;
      },
      error: () => {
        this.toastService.show('Failed to load inventory', 'error');
        this.isLoading = false;
      }
    });
  }

  confirmDelete(id: string): void {
    this.productToDelete = id;
    this.isConfirmOpen = true;
  }

  onDeleteConfirmed(): void {
    if (!this.productToDelete) {
      return;
    }

    this.productService.delete(this.productToDelete).subscribe({
      next: () => {
        this.toastService.show('Product deleted successfully', 'success');
        this.products = this.products.filter((product) => product.id !== this.productToDelete);
        this.productToDelete = null;
        this.isConfirmOpen = false;
      },
      error: () => {
        this.toastService.show('Failed to delete product', 'error');
        this.productToDelete = null;
        this.isConfirmOpen = false;
      }
    });
  }

  onDeleteCancelled(): void {
    this.productToDelete = null;
    this.isConfirmOpen = false;
  }
}
