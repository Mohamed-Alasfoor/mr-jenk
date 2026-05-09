import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LucideAngularModule, ImageIcon, X, Expand } from 'lucide-angular';
import { MediaImageComponent } from '../media-image/media-image.component';

@Component({
  selector: 'app-image-gallery',
  standalone: true,
  imports: [CommonModule, LucideAngularModule, MediaImageComponent],
  templateUrl: './image-gallery.component.html',
  styleUrl: './image-gallery.component.scss'
})
export class ImageGalleryComponent {
  @Input() imageUrls: string[] = [];
  
  selectedIndex = 0;
  readonly ImageIconIcon = ImageIcon;
  readonly XIcon = X;
  readonly ExpandIcon = Expand;
  isLightboxOpen = false;

  get mainImage(): string | null {
    return this.imageUrls?.length ? this.imageUrls[this.selectedIndex] : null;
  }

  selectImage(index: number) {
    this.selectedIndex = index;
  }

  openLightbox(): void {
    if (!this.mainImage) {
      return;
    }

    this.isLightboxOpen = true;
  }

  closeLightbox(): void {
    this.isLightboxOpen = false;
  }
}
