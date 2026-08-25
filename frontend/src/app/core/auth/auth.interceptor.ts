import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { AuthService } from './auth.service';

/** Endpoints reachable without a token — a 401 from these is a normal answer, not a dead session. */
const PUBLIC_PATHS = [
  'v1/auth/login',
  'v1/auth/activate',
  'v1/auth/forgot-password',
  'v1/auth/reset-password',
];

/**
 * Attaches the bearer token and treats a 401 as the end of the session.
 *
 * The backend revokes tokens on offboarding or a password change rather than waiting for
 * them to expire, so a 401 can arrive at any moment on a token the client still believes
 * is valid. Clearing local state here is what turns that into a clean redirect to the login
 * screen instead of a broken-looking page.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();
  const isPublic = PUBLIC_PATHS.some((path) => req.url.includes(path));

  const request =
    token && !isPublic ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isPublic) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
