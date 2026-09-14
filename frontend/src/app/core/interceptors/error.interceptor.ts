import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';

/**
 * Surfaces unexpected failures as a snackbar so they cannot vanish into the console.
 *
 * 4xx responses are left alone: those are answers a component asked for and renders itself
 * (a duplicate email belongs on the email field, not in a toast). Only 0 and 5xx — the
 * failures no component can meaningfully explain — are announced here.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && (error.status === 0 || error.status >= 500)) {
        const message =
          error.status === 0
            ? 'Cannot reach the TechConnect server. Check your connection.'
            : 'Something went wrong on the server. Please try again.';
        snackBar.open(message, 'Dismiss', { duration: 6000 });
      }
      return throwError(() => error);
    }),
  );
};
