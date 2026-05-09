import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Product } from '../../../core/models/product.model';
import { MediaImageComponent } from '../media-image/media-image.component';

@Component({
  selector: 'app-product-card',
  standalone: true,
  imports: [CommonModule, RouterLink, MediaImageComponent],
  templateUrl: './product-card.component.html',
  styleUrl: './product-card.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ProductCardComponent {
  private _product!: Product;

  @Input({ required: true })
  set product(value: Product) {
    this._product = value;
    this.primaryImageUrl = value.imageUrls?.[0] ?? null;
  }

  get product(): Product {
    return this._product;
  }

  primaryImageUrl: string | null = null;

  get stockLabel(): string {
    if (this.product.quantity === 0) {
      return 'Unavailable';
    }

    if (this.product.quantity < 5) {
      return 'Low stock';
    }

    return 'Available';
  }
}
