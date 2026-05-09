import { CommonModule } from '@angular/common';
import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { LucideAngularModule, Image as ImageIcon } from 'lucide-angular';

@Component({
  selector: 'app-media-image',
  standalone: true,
  imports: [CommonModule, LucideAngularModule],
  template: `
    <div class="image-shell">
      <div class="image-placeholder" *ngIf="showLoader" aria-hidden="true">
        <div class="image-shimmer"></div>
        <div class="image-spinner"></div>
      </div>

      <img
        *ngIf="src && !hasError"
        [src]="src"
        [alt]="alt"
        [attr.loading]="loading"
        [attr.decoding]="decoding"
        [style.object-fit]="objectFit"
        [style.object-position]="objectPosition"
        [class.loaded]="isLoaded"
        (load)="handleLoad()"
        (error)="handleError()">

      <div class="image-fallback" *ngIf="!src || hasError">
        <lucide-icon [img]="ImageIconIcon" [size]="fallbackIconSize"></lucide-icon>
      </div>
    </div>
  `,
  styles: [`
    :host {
      display: block;
      width: 100%;
      height: 100%;
    }

    .image-shell {
      position: relative;
      width: 100%;
      height: 100%;
      overflow: hidden;
      background:
        linear-gradient(135deg, rgba(255, 255, 255, 0.94), rgba(229, 237, 255, 0.92));
    }

    .image-placeholder,
    .image-fallback {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .image-placeholder {
      background:
        linear-gradient(135deg, rgba(255, 255, 255, 0.65), rgba(229, 237, 255, 0.3));
    }

    .image-shimmer {
      position: absolute;
      inset: 0;
      background:
        linear-gradient(
          110deg,
          rgba(255, 255, 255, 0) 25%,
          rgba(255, 255, 255, 0.72) 50%,
          rgba(255, 255, 255, 0) 75%
        );
      transform: translateX(-100%);
      animation: media-image-shimmer 1.2s ease-in-out infinite;
    }

    .image-spinner {
      position: relative;
      z-index: 1;
      width: 1.8rem;
      height: 1.8rem;
      border: 2px solid rgba(var(--color-accent-primary-rgb), 0.16);
      border-top-color: rgba(var(--color-accent-primary-rgb), 0.95);
      border-radius: 50%;
      animation: media-image-spin 0.9s linear infinite;
    }

    img {
      width: 100%;
      height: 100%;
      opacity: 0;
      transition: opacity 220ms ease, transform 300ms ease;
    }

    img.loaded {
      opacity: 1;
    }

    .image-fallback {
      color: var(--color-text-tertiary);
      opacity: 0.7;
      background:
        radial-gradient(circle at top, rgba(var(--color-accent-cyan-rgb), 0.16), transparent 55%),
        linear-gradient(135deg, rgba(255, 255, 255, 0.9), rgba(229, 237, 255, 0.94));
    }

    :host-context(.zoom-on-hover:hover) img.loaded {
      transform: scale(1.04);
    }

    @keyframes media-image-shimmer {
      100% {
        transform: translateX(100%);
      }
    }

    @keyframes media-image-spin {
      to {
        transform: rotate(360deg);
      }
    }
  `]
})
export class MediaImageComponent implements OnChanges {
  @Input() src: string | null = null;
  @Input() alt = '';
  @Input() loading: 'lazy' | 'eager' = 'lazy';
  @Input() decoding: 'async' | 'sync' | 'auto' = 'async';
  @Input() objectFit: 'cover' | 'contain' | 'fill' | 'none' | 'scale-down' = 'cover';
  @Input() objectPosition = 'center';
  @Input() fallbackIconSize = 32;

  readonly ImageIconIcon = ImageIcon;
  isLoaded = false;
  hasError = false;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['src']) {
      this.isLoaded = false;
      this.hasError = false;
    }
  }

  get showLoader(): boolean {
    return !!this.src && !this.isLoaded && !this.hasError;
  }

  handleLoad(): void {
    this.isLoaded = true;
  }

  handleError(): void {
    this.hasError = true;
    this.isLoaded = false;
  }
}
