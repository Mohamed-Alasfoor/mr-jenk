import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';
import { filter, map, take } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/user.model';

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const expectedRoles = route.data['roles'] as Role[];

  return authService.authReady$.pipe(
    filter(Boolean),
    take(1),
    map(() => {
      if (!authService.isLoggedIn()) {
        return router.parseUrl('/auth/login');
      }

      const userRole = authService.currentUserValue?.role;
      return userRole && expectedRoles.includes(userRole)
        ? true
        : router.parseUrl('/403');
    })
  );
};
