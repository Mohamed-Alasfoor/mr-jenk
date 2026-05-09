import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-skeleton',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="skeleton-card" [class.product]="type === 'product'">
      <div class="shimmer"></div>
      <ng-container *ngIf="type === 'product'">
        <div class="skeleton-image"></div>
        <div class="skeleton-content">
          <div class="skeleton-line title"></div>
          <div class="skeleton-line price"></div>
        </div>
      </ng-container>
    </div>
  `,
  styles: [`
    .skeleton-card {
      position: relative;
      overflow: hidden;
      background: rgba(255, 255, 255, 0.76);
      border-radius: var(--radius-lg);
      border: 1px solid rgba(255, 255, 255, 0.72);
      box-shadow: var(--shadow-neu-sm), var(--shadow-inset);
    }

    .skeleton-card.product {
      height: 100%;
      display: flex;
      flex-direction: column;
    }
    
    .skeleton-image {
      width: 100%;
      aspect-ratio: 1/1;
      background: rgba(229, 237, 255, 0.86);
    }
    
    .skeleton-content {
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      flex: 1;
    }
    
    .skeleton-line {
      height: 1.5rem;
      background: rgba(229, 237, 255, 0.86);
      border-radius: 999px;
    }

    .skeleton-line.title { width: 80%; }
    .skeleton-line.price { width: 40%; }
    
    .shimmer {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: linear-gradient(
        90deg,
        transparent,
        rgba(255, 255, 255, 0.66),
        transparent
      );
      transform: translateX(-100%);
      animation: shimmer 1.5s infinite;
      z-index: 10;
    }
    
    @keyframes shimmer {
      100% { transform: translateX(100%); }
    }
  `]
  ,
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SkeletonComponent {
  @Input() type: 'product' | 'text' = 'product';
}
