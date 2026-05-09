import { AfterViewInit, Component, DestroyRef, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavbarComponent } from './shared/components/navbar/navbar.component';
import { FooterComponent } from './shared/components/footer/footer.component';
import { ToastComponent } from './shared/components/toast/toast.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, RouterOutlet, NavbarComponent, FooterComponent, ToastComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements AfterViewInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private intersectionObserver: IntersectionObserver | null = null;
  private mutationObserver: MutationObserver | null = null;
  private revealScanQueued = false;
  private routeLoaderTimeoutId: number | null = null;

  title = 'Buy-01';
  isRouteLoading = false;

  ngAfterViewInit(): void {
    this.setupScrollReveal();
    this.router.events
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((event) => {
        if (event instanceof NavigationStart) {
          if (this.routeLoaderTimeoutId !== null) {
            clearTimeout(this.routeLoaderTimeoutId);
            this.routeLoaderTimeoutId = null;
          }

          this.isRouteLoading = true;
          return;
        }

        if (event instanceof NavigationEnd) {
          this.scheduleScrollRevealScan();
          this.scheduleRouteLoaderHide();
          return;
        }

        if (event instanceof NavigationCancel || event instanceof NavigationError) {
          this.scheduleRouteLoaderHide();
        }
      });
  }

  ngOnDestroy(): void {
    this.intersectionObserver?.disconnect();
    this.mutationObserver?.disconnect();
    if (this.routeLoaderTimeoutId !== null) {
      clearTimeout(this.routeLoaderTimeoutId);
    }
  }

  private setupScrollReveal(): void {
    if (typeof window === 'undefined') {
      return;
    }

    if ('IntersectionObserver' in window) {
      this.intersectionObserver = new IntersectionObserver(
        (entries) => {
          entries.forEach((entry) => {
            if (entry.isIntersecting) {
              entry.target.classList.add('revealed');
              this.intersectionObserver?.unobserve(entry.target);
            }
          });
        },
        {
          threshold: 0.12,
          rootMargin: '0px 0px -8% 0px'
        }
      );
    }

    if ('MutationObserver' in window) {
      this.mutationObserver = new MutationObserver(() => this.scheduleScrollRevealScan());
      this.mutationObserver.observe(document.querySelector('main') ?? document.body, {
        childList: true,
        subtree: true
      });
    }

    this.scheduleScrollRevealScan();
  }

  private observeScrollRevealElements(): void {
    const elements = document.querySelectorAll<HTMLElement>('.scroll-reveal:not(.revealed)');
    if (elements.length === 0) {
      return;
    }

    if (
      !this.intersectionObserver ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches
    ) {
      elements.forEach((element) => element.classList.add('revealed'));
      return;
    }

    elements.forEach((element) => this.intersectionObserver?.observe(element));
  }

  private scheduleScrollRevealScan(): void {
    if (this.revealScanQueued) {
      return;
    }

    this.revealScanQueued = true;
    requestAnimationFrame(() => {
      this.revealScanQueued = false;
      this.observeScrollRevealElements();
    });
  }

  private scheduleRouteLoaderHide(): void {
    if (typeof window === 'undefined') {
      this.isRouteLoading = false;
      return;
    }

    if (this.routeLoaderTimeoutId !== null) {
      clearTimeout(this.routeLoaderTimeoutId);
    }

    this.routeLoaderTimeoutId = window.setTimeout(() => {
      this.isRouteLoading = false;
      this.routeLoaderTimeoutId = null;
    }, 160);
  }
}
