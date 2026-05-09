import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Product } from '../../../core/models/product.model';
import { PublicSellerProfile } from '../../../core/models/user.model';
import { ProductService } from '../../../core/services/product.service';
import { SellerService } from '../../../core/services/seller.service';
import { ToastService } from '../../../core/services/toast.service';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { MediaImageComponent } from '../../../shared/components/media-image/media-image.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'app-seller-storefront',
  standalone: true,
  imports: [
    CommonModule,
    RouterLink,
    EmptyStateComponent,
    MediaImageComponent,
    ProductCardComponent,
    SkeletonComponent
  ],
  templateUrl: './seller-storefront.component.html',
  styleUrl: './seller-storefront.component.scss'
})
export class SellerStorefrontComponent implements OnInit {
  route = inject(ActivatedRoute);
  productService = inject(ProductService);
  sellerService = inject(SellerService);
  toastService = inject(ToastService);

  seller: PublicSellerProfile | null = null;
  products: Product[] = [];
  isLoadingSeller = true;
  isLoadingProducts = true;
  isNotFound = false;
  hasLoadError = false;

  readonly skeletonArray = Array(6).fill(0);

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const sellerId = params.get('id');
      if (sellerId) {
        this.loadStorefront(sellerId);
      }
    });
  }

  get sellerInitial(): string {
    return this.seller?.fullName?.trim().charAt(0).toUpperCase() || 'S';
  }

  get productsWithImagesCount(): number {
    return this.products.filter((product) => product.imageUrls.length > 0).length;
  }

  loadStorefront(sellerId: string): void {
    this.seller = null;
    this.products = [];
    this.isNotFound = false;
    this.hasLoadError = false;
    this.isLoadingSeller = true;
    this.isLoadingProducts = true;

    this.sellerService.getById(sellerId).subscribe({
      next: (seller) => {
        if (this.route.snapshot.paramMap.get('id') !== sellerId) {
          return;
        }

        this.seller = seller;
        this.isLoadingSeller = false;
      },
      error: (error) => {
        if (this.route.snapshot.paramMap.get('id') !== sellerId) {
          return;
        }

        this.isLoadingSeller = false;
        this.isLoadingProducts = false;

        if (error.status === 404) {
          this.isNotFound = true;
          return;
        }

        this.hasLoadError = true;
      }
    });

    this.productService.getBySellerId(sellerId).subscribe({
      next: (products) => {
        if (this.route.snapshot.paramMap.get('id') !== sellerId) {
          return;
        }

        this.products = products;
        this.isLoadingProducts = false;
      },
      error: () => {
        if (this.route.snapshot.paramMap.get('id') !== sellerId) {
          return;
        }

        this.isLoadingProducts = false;
        if (!this.isNotFound && this.seller) {
          this.toastService.show('Failed to load this seller catalog.', 'error');
        }
      }
    });
  }

  trackByProductId(index: number, product: Product): string {
    return product.id;
  }
}
