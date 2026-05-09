import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ProductService } from '../../core/services/product.service';
import { Product } from '../../core/models/product.model';
import { ProductCardComponent } from '../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { ToastService } from '../../core/services/toast.service';
import { MediaImageComponent } from '../../shared/components/media-image/media-image.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink, ProductCardComponent, SkeletonComponent, MediaImageComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss'
})
export class HomeComponent implements OnInit {
  authService = inject(AuthService);
  productService = inject(ProductService);
  toastService = inject(ToastService);

  featuredProducts: Product[] = [];
  isLoading = true;
  skeletonArray = Array(6).fill(0);

  ngOnInit(): void {
    this.productService.getAll().subscribe({
      next: (products) => {
        this.featuredProducts = products.slice(0, 6);
        this.isLoading = false;
      },
      error: () => {
        this.toastService.show('Failed to load featured products.', 'error');
        this.isLoading = false;
      }
    });
  }

  get heroProduct(): Product | null {
    return this.featuredProducts[0] ?? null;
  }

  get curatedInventory(): Product[] {
    return this.featuredProducts.slice(1);
  }

  trackByProductId(index: number, product: Product): string {
    return product.id;
  }
}
