import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product.model';
import { PublicSellerProfile } from '../../../core/models/user.model';
import { SellerService } from '../../../core/services/seller.service';
import { ImageGalleryComponent } from '../../../shared/components/image-gallery/image-gallery.component';
import { MediaImageComponent } from '../../../shared/components/media-image/media-image.component';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../../core/services/toast.service';

@Component({
  selector: 'app-product-detail',
  standalone: true,
  imports: [CommonModule, RouterLink, ImageGalleryComponent, MediaImageComponent, ProductCardComponent, SkeletonComponent],
  templateUrl: './product-detail.component.html',
  styleUrl: './product-detail.component.scss'
})
export class ProductDetailComponent implements OnInit {
  route = inject(ActivatedRoute);
  productService = inject(ProductService);
  sellerService = inject(SellerService);
  toastService = inject(ToastService);

  product: Product | null = null;
  seller: PublicSellerProfile | null = null;
  moreFromSeller: Product[] = [];
  sellerCatalogCount = 0;

  isLoading = true;
  isLoadingSeller = false;
  isLoadingMoreFromSeller = false;
  isNotFound = false;
  loadingStage = 'Loading product page';

  skeletonArray = Array(3).fill(0);

  ngOnInit() {
    this.route.paramMap.subscribe(params => {
      const id = params.get('id');
      if (id) {
        this.loadProduct(id);
      }
    });
  }

  loadProduct(id: string) {
    this.isLoading = true;
    this.isNotFound = false;
    this.product = null;
    this.resetSellerData();
    this.loadingStage = 'Getting product details';
    
    this.productService.getById(id).subscribe({
      next: (product: Product) => {
        this.product = product;
        this.loadingStage = 'Getting the gallery and seller page ready';
        this.isLoading = false;
        this.loadSellerData(product);
      },
      error: (err: any) => {
        this.isLoading = false;
        if (err.status === 404) {
          this.isNotFound = true;
        } else {
          this.toastService.show('Failed to load product details.', 'error');
        }
      }
    });
  }

  get sellerInitial(): string {
    return this.seller?.fullName?.trim().charAt(0).toUpperCase() || 'S';
  }

  get loadingSteps(): Array<{ label: string; description: string; done: boolean; active: boolean }> {
    const isPreparing = this.loadingStage === 'Getting the gallery and seller page ready';

    return [
      {
        label: 'Product record',
        description: 'Loading the main product details.',
        done: isPreparing,
        active: !isPreparing
      },
      {
        label: 'Gallery setup',
        description: 'Preparing the images, price, and product summary.',
        done: false,
        active: isPreparing
      },
      {
        label: 'Seller context',
        description: 'Loading the seller profile and more items from this shop.',
        done: false,
        active: false
      }
    ];
  }

  loadSellerData(product: Product): void {
    const { id: productId, sellerId } = product;

    this.isLoadingSeller = true;
    this.isLoadingMoreFromSeller = true;

    this.sellerService.getById(sellerId).subscribe({
      next: (seller) => {
        if (this.product?.id !== productId) {
          return;
        }

        this.seller = seller;
        this.isLoadingSeller = false;
      },
      error: () => {
        if (this.product?.id !== productId) {
          return;
        }

        this.isLoadingSeller = false;
      }
    });

    this.productService.getBySellerId(sellerId).subscribe({
      next: (products) => {
        if (this.product?.id !== productId) {
          return;
        }

        this.sellerCatalogCount = products.length;
        this.moreFromSeller = products
          .filter((candidate) => candidate.id !== productId)
          .slice(0, 3);
        this.isLoadingMoreFromSeller = false;
      },
      error: () => {
        if (this.product?.id !== productId) {
          return;
        }

        this.isLoadingMoreFromSeller = false;
      }
    });
  }

  trackByProductId(index: number, product: Product): string {
    return product.id;
  }

  private resetSellerData(): void {
    this.seller = null;
    this.moreFromSeller = [];
    this.sellerCatalogCount = 0;
    this.isLoadingSeller = false;
    this.isLoadingMoreFromSeller = false;
  }
}
