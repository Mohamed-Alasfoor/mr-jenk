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
  selectedCategory = '';
  categories: string[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
  catalogReady = false;
  private requestSequence = 0;

  ngOnInit(): void {
    this.loadProducts();
  }

  loadProducts(): void {
    const requestId = ++this.requestSequence;
    this.isLoading = true;
    this.productService.search({
      keyword: this.searchTerm,
      category: this.selectedCategory,
      minPrice: this.catalogReady ? this.selectedMinPrice : undefined,
      maxPrice: this.catalogReady ? this.selectedMaxPrice : undefined,
      availability: this.availabilityFilter,
      sort: this.sortOption,
      page: this.page,
      size: 12
    }).subscribe({
      next: (result) => {
        if (requestId !== this.requestSequence) return;
        this.products = result.items;
        this.categories = result.categories;
        this.totalPages = result.totalPages;
        this.totalElements = result.totalElements;
        if (!this.catalogReady) {
          this.catalogMinPrice = result.minPrice;
          this.catalogMaxPrice = result.maxPrice;
          this.sliderMaxPrice = result.maxPrice > result.minPrice ? result.maxPrice : result.maxPrice + 1;
          this.selectedMinPrice = result.minPrice;
          this.selectedMaxPrice = result.maxPrice;
          this.catalogReady = true;
        }
        this.isLoading = false;
      },
      error: () => {
        if (requestId !== this.requestSequence) return;
        this.toastService.show('Failed to load products.', 'error');
        this.isLoading = false;
      }
    });
  }

  applyFilters(): void { this.page = 0; this.loadProducts(); }
  goToPage(page: number): void { if (page < 0 || page >= this.totalPages) return; this.page = page; this.loadProducts(); }

  get filteredProducts(): Product[] {
    return this.products;
  }

  get hasActiveFilters(): boolean {
    return (
      this.searchTerm.trim().length > 0 ||
      this.selectedMinPrice > this.catalogMinPrice ||
      this.selectedMaxPrice < this.catalogMaxPrice ||
      this.availabilityFilter !== 'all' ||
      this.selectedCategory !== '' ||
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
    this.selectedCategory = '';
    this.page = 0;
    this.loadProducts();
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
