import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const publicOnlyGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.authReady$.pipe(
    filter(Boolean),
    take(1),
    map(() => {
      if (authService.isLoggedIn()) {
        const userRole = authService.currentUserValue?.role;
        if (userRole === 'SELLER') {
          return router.parseUrl('/seller/dashboard');
        }
        return router.parseUrl('/products');
      }

      return true;
    })
  );
};
