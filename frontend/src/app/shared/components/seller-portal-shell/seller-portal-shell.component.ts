import { ChangeDetectionStrategy, Component, Input, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import {
  LayoutDashboard,
  Images,
  LogOut,
  Package,
  Settings,
  Store
} from 'lucide-angular';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/services/auth.service';
import { MediaImageComponent } from '../media-image/media-image.component';

@Component({
  selector: 'app-seller-portal-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, LucideAngularModule, MediaImageComponent],
  templateUrl: './seller-portal-shell.component.html',
  styleUrl: './seller-portal-shell.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class SellerPortalShellComponent {
  private router = inject(Router);

  authService = inject(AuthService);

  @Input() kicker = '';
  @Input() title = '';
  @Input() description = '';
  @Input() sidebarActionLabel = 'List New Product';
  @Input() sidebarActionLink: string | any[] = '/seller/products/new';

  readonly LayoutDashboardIcon = LayoutDashboard;
  readonly PackageIcon = Package;
  readonly ImagesIcon = Images;
  readonly SettingsIcon = Settings;
  readonly StoreIcon = Store;
  readonly LogOutIcon = LogOut;

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }
}
