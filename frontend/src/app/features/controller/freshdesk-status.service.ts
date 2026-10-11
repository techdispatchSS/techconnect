import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, interval, of, startWith, switchMap } from 'rxjs';

import { ControllerApiService } from './controller-api.service';
import { FreshdeskStatus } from './controller.models';

const POLL_INTERVAL_MS = 30_000;

/**
 * The single source of "how is Freshdesk doing" for the dashboard: the header badge reads it to
 * say so truthfully, and the queue reads it to know whether the simulation controls apply.
 *
 * <p>Root-scoped like `NewIncidentNotifierService`; started once from the shell, and `start`
 * resets so a second sign-in on the same tab gets a fresh poll rather than a stale value.
 */
@Injectable({ providedIn: 'root' })
export class FreshdeskStatusService {
  private readonly api = inject(ControllerApiService);

  /** Null until the first answer lands (or if the status can't be read at all). */
  readonly status = signal<FreshdeskStatus | null>(null);

  start(destroyRef: DestroyRef): void {
    this.status.set(null);

    interval(POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.freshdeskStatus().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((status) => {
        if (status) {
          this.status.set(status);
        }
      });
  }

  /** Re-reads now, e.g. right after a simulated ticket so "synced just now" is accurate. */
  refresh(): void {
    this.api.freshdeskStatus().subscribe({
      next: (status) => this.status.set(status),
      error: () => undefined,
    });
  }
}
