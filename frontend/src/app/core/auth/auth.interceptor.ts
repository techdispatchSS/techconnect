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
 * True for a request the browser will resolve against this page's own origin: a relative
 * URL (`apiPrefixInterceptor` rewrites every real API call to one of these, e.g.
 * `/api/v1/...`), or an absolute URL that explicitly names this origin.
 *
 * This is what the token gets attached to — never the inverse (a denylist of "public"
 * paths). A denylist only protects the specific third-party domains someone thought to
 * list; an allowlist means a session token can never leak to a domain nobody has written
 * yet, which matters the day a feature calls an absolute URL (a maps API, an analytics
 * endpoint, any CDN) and nobody remembers to update this file.
 */
function isSameOrigin(url: string): boolean {
  return !/^https?:\/\//i.test(url) || url.startsWith(location.origin);
}

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
    token && !isPublic && isSameOrigin(req.url)
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isPublic) {
        auth.logout();
      }
      return throwError(() => error);
    }),
  );
};
