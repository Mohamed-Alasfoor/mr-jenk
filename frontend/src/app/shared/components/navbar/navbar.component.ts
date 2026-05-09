import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  NgZone,
  OnDestroy,
  OnInit,
  inject
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LogOut } from 'lucide-angular';
import { LucideAngularModule } from 'lucide-angular';
import { AuthService } from '../../../core/services/auth.service';
import { Observable } from 'rxjs';
import { User as UserModel } from '../../../core/models/user.model';
import { MediaImageComponent } from '../media-image/media-image.component';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, LucideAngularModule, MediaImageComponent],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class NavbarComponent implements OnInit, OnDestroy {
  private readonly ngZone = inject(NgZone);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly router = inject(Router);
  private removeScrollListener?: () => void;

  authService = inject(AuthService);
  currentUser$: Observable<UserModel | null> = this.authService.currentUser$;
  isScrolled = false;
  readonly LogOutIcon = LogOut;

  ngOnInit() {
    if (typeof window === 'undefined') {
      return;
    }

    this.isScrolled = window.scrollY > 60;
    this.ngZone.runOutsideAngular(() => {
      const onScroll = () => {
        const nextScrolledState = window.scrollY > 60;
        if (nextScrolledState === this.isScrolled) {
          return;
        }

        this.ngZone.run(() => {
          this.isScrolled = nextScrolledState;
          this.cdr.markForCheck();
        });
      };

      window.addEventListener('scroll', onScroll, { passive: true });
      this.removeScrollListener = () => window.removeEventListener('scroll', onScroll);
    });
  }

  getProfileRoute(user: UserModel): string {
    return user.role === 'SELLER' ? '/seller/profile' : '/profile';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/auth/login']);
  }

  ngOnDestroy(): void {
    this.removeScrollListener?.();
  }
}
