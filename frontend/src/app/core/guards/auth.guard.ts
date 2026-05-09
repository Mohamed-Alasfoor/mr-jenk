import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  return authService.authReady$.pipe(
    filter(Boolean),
    take(1),
    map(() => authService.isLoggedIn() ? true : router.parseUrl('/auth/login'))
  );
};
