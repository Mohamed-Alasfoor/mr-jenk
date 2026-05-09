import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const toastService = inject(ToastService);
  const token = localStorage.getItem('buy01_token');

  let authReq = req;
  if (token) {
    authReq = req.clone({
      headers: req.headers.set('Authorization', `Bearer ${token}`)
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      const isAuthRequest = req.url.includes('/auth/login') || req.url.includes('/auth/register');
      const currentPath = router.url;
      const isProtectedPage = currentPath === '/profile' || currentPath.startsWith('/seller');
      const isProfileBootstrapRequest = req.url.endsWith('/me') && !isProtectedPage;

      if (error.status === 401) {
        if (!isAuthRequest) {
          authService.logout();
          toastService.show('Your session expired. Please sign in again.', 'warning');
          if (!isProfileBootstrapRequest) {
            router.navigate(['/auth/login']);
          }
        }
      } else if (error.status === 403) {
        toastService.show('You do not have access to that action.', 'warning');
        router.navigate(['/403']);
      } else if (error.status === 429) {
        toastService.show(
          error.error?.message || 'Too many requests. Please wait before trying again.',
          'warning'
        );
      }
      return throwError(() => error);
    })
  );
};
