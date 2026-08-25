import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { UserRole } from './auth.models';
import { AuthService } from './auth.service';

/** Blocks unauthenticated access, remembering where the user was headed. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return router.createUrlTree(['/login'], { queryParams: { redirectTo: state.url } });
};

/**
 * Restricts a route to specific roles, read from `data: { roles: [...] }`.
 *
 * This is a usability guard, not a security boundary — the backend enforces the same rules
 * on every request (FR-01). Its job is to keep a Technician from being shown an admin screen
 * that would only fail with a 403 once it tried to load.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.createUrlTree(['/login']);
  }

  const allowed = (route.data['roles'] as UserRole[] | undefined) ?? [];
  if (allowed.length === 0 || auth.hasRole(...allowed)) {
    return true;
  }

  return router.createUrlTree(['/forbidden']);
};
