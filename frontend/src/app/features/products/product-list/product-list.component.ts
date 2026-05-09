import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ProductService } from '../../../core/services/product.service';
import { Product } from '../../../core/models/product.model';
import { ProductCardComponent } from '../../../shared/components/product-card/product-card.component';
import { SkeletonComponent } from '../../../shared/components/skeleton/skeleton.component';
import { EmptyStateComponent } from '../../../shared/components/empty-state/empty-state.component';
import { ToastService } from '../../../core/services/toast.service';

type SortOption = 'newest' | 'oldest' | 'price-low' | 'price-high' | 'stock';
type AvailabilityOption = 'all' | 'available' | 'low-stock' | 'sold-out';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, FormsModule, ProductCardComponent, SkeletonComponent, EmptyStateComponent],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.scss'
})
export class ProductListComponent implements OnInit {
  productService = inject(ProductService);
  toastService = inject(ToastService);

  products: Product[] = [];
  isLoading = true;
  skeletonArray = Array(8).fill(0);

  searchTerm = '';
  catalogMinPrice = 0;
  catalogMaxPrice = 1000;
  sliderMaxPrice = 1000;
  selectedMinPrice = 0;
  selectedMaxPrice = 1000;
  availabilityFilter: AvailabilityOption = 'all';
  sortOption: SortOption = 'newest';

  ngOnInit(): void {
    this.productService.getAll().subscribe({
      next: (data) => {
        this.products = data;
        this.syncPriceBounds(data);
        this.isLoading = false;
      },
      error: () => {
        this.toastService.show('Failed to load products.', 'error');
        this.isLoading = false;
      }
    });
  }

  get filteredProducts(): Product[] {
    const search = this.searchTerm.trim().toLowerCase();
    const minPrice = Math.min(this.selectedMinPrice, this.selectedMaxPrice);
    const maxPrice = Math.max(this.selectedMinPrice, this.selectedMaxPrice);

    const filteredProducts = this.products.filter((product) => {
      const productName = product.name.toLowerCase();
      const productDescription = product.description.toLowerCase();
      const matchesSearch =
        search.length === 0 ||
        productName.includes(search) ||
        productDescription.includes(search);

      const matchesMinPrice = minPrice === null || product.price >= minPrice;
      const matchesMaxPrice = maxPrice === null || product.price <= maxPrice;
      const matchesAvailability = this.matchesAvailability(product);

      return matchesSearch && matchesMinPrice && matchesMaxPrice && matchesAvailability;
    });

    return filteredProducts.sort((left, right) => this.compareProducts(left, right));
  }

  get hasActiveFilters(): boolean {
    return (
      this.searchTerm.trim().length > 0 ||
      this.selectedMinPrice > this.catalogMinPrice ||
      this.selectedMaxPrice < this.catalogMaxPrice ||
      this.availabilityFilter !== 'all' ||
      this.sortOption !== 'newest'
    );
  }

  get minThumbPercent(): number {
    return this.toPercent(this.selectedMinPrice);
  }

  get maxThumbPercent(): number {
    return this.toPercent(this.selectedMaxPrice);
  }

  get rangeFillLeftPercent(): number {
    return Math.min(this.minThumbPercent, this.maxThumbPercent);
  }

  get rangeFillWidthPercent(): number {
    return Math.max(this.maxThumbPercent - this.minThumbPercent, 0);
  }

  clearFilters(): void {
    this.searchTerm = '';
    this.selectedMinPrice = this.catalogMinPrice;
    this.selectedMaxPrice = this.catalogMaxPrice;
    this.availabilityFilter = 'all';
    this.sortOption = 'newest';
  }

  onMinPriceChange(value: string | number | null): void {
    const nextMin = this.normalizePrice(value, this.catalogMinPrice);
    this.selectedMinPrice = Math.min(nextMin, this.selectedMaxPrice);
  }

  onMaxPriceChange(value: string | number | null): void {
    const nextMax = this.normalizePrice(value, this.catalogMaxPrice);
    this.selectedMaxPrice = Math.max(nextMax, this.selectedMinPrice);
  }

  trackByProductId(index: number, product: Product): string {
    return product.id;
  }

  private compareProducts(left: Product, right: Product): number {
    switch (this.sortOption) {
      case 'oldest':
        return new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
      case 'price-low':
        return left.price - right.price;
      case 'price-high':
        return right.price - left.price;
      case 'stock':
        return right.quantity - left.quantity;
      case 'newest':
      default:
        return new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime();
    }
  }

  private matchesAvailability(product: Product): boolean {
    switch (this.availabilityFilter) {
      case 'available':
        return product.quantity > 0;
      case 'low-stock':
        return product.quantity > 0 && product.quantity < 5;
      case 'sold-out':
        return product.quantity === 0;
      case 'all':
      default:
        return true;
    }
  }

  private syncPriceBounds(products: Product[]): void {
    if (products.length === 0) {
      this.catalogMinPrice = 0;
      this.catalogMaxPrice = 1000;
      this.sliderMaxPrice = 1000;
      this.selectedMinPrice = 0;
      this.selectedMaxPrice = 1000;
      return;
    }

    const prices = products
      .map((product) => product.price)
      .filter((price) => Number.isFinite(price))
      .sort((left, right) => left - right);

    const minPrice = prices[0];
    const maxPrice = prices[prices.length - 1];

    this.catalogMinPrice = Number(minPrice.toFixed(2));
    this.catalogMaxPrice = Number(maxPrice.toFixed(2));
    this.sliderMaxPrice = this.catalogMaxPrice > this.catalogMinPrice
      ? this.catalogMaxPrice
      : Number((this.catalogMaxPrice + 1).toFixed(2));
    this.selectedMinPrice = this.catalogMinPrice;
    this.selectedMaxPrice = this.catalogMaxPrice;
  }

  private normalizePrice(value: string | number | null | undefined, fallback: number): number {
    if (value === null || value === undefined || value === '') {
      return fallback;
    }

    const parsedValue = typeof value === 'number'
      ? value
      : Number(String(value).replace(',', '.'));

    if (!Number.isFinite(parsedValue)) {
      return fallback;
    }

    const clamped = Math.min(Math.max(parsedValue, this.catalogMinPrice), this.sliderMaxPrice);
    return Number(clamped.toFixed(2));
  }

  private toPercent(value: number): number {
    const span = this.sliderMaxPrice - this.catalogMinPrice;
    if (span <= 0) {
      return 0;
    }

    return ((value - this.catalogMinPrice) / span) * 100;
  }
}
