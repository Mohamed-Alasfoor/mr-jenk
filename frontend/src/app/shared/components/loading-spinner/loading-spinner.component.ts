import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="spinner-container" [class.full-page]="fullPage" [class.compact]="compact">
      <div class="spinner" [style.width.px]="size" [style.height.px]="size"></div>
    </div>
  `,
  styles: [`
    .spinner-container {
      display: flex;
      justify-content: center;
      align-items: center;
      padding: 2rem;
    }

    .spinner-container.full-page {
      height: 100vh;
      width: 100vw;
      position: fixed;
      top: 0;
      left: 0;
      background: var(--color-bg-base);
      z-index: 9999;
    }

    .spinner-container.compact {
      padding: 0;
    }

    .spinner {
      border: 3px solid rgba(92, 98, 255, 0.14);
      border-radius: 50%;
      border-top-color: var(--color-accent-primary);
      box-shadow: 0 0 18px rgba(92, 98, 255, 0.14);
      animation: spin 1s ease-in-out infinite;
    }
    @keyframes spin {
      to { transform: rotate(360deg); }
    }
  `]
})
export class LoadingSpinnerComponent {
  @Input() fullPage = false;
  @Input() compact = false;
  @Input() size = 40;
}
