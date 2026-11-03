import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, interval, merge, of, startWith, switchMap } from 'rxjs';

import { RealtimeService } from '../../core/realtime/realtime.service';
import { ControllerApiService } from './controller-api.service';
import { JobCounts } from './controller.models';

const POLL_INTERVAL_MS = 30_000;

/**
 * How many jobs are in each stage, for the sidebar's badges. Polled like the Freshdesk status:
 * started once from the shell, and quietly keeps the last value if a poll fails.
 */
@Injectable({ providedIn: 'root' })
export class JobCountsService {
  private readonly api = inject(ControllerApiService);
  private readonly realtime = inject(RealtimeService);

  readonly counts = signal<JobCounts | null>(null);

  start(destroyRef: DestroyRef): void {
    this.counts.set(null);
    merge(interval(POLL_INTERVAL_MS).pipe(startWith(0)), this.realtime.on('JOB_CHANGED'))
      .pipe(
        switchMap(() => this.api.jobCounts().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((counts) => {
        if (counts) {
          this.counts.set(counts);
        }
      });
  }

  /** Re-reads now, e.g. right after closing a job so the badge is not a poll behind. */
  refresh(): void {
    this.api.jobCounts().subscribe({
      next: (counts) => this.counts.set(counts),
      error: () => undefined,
    });
  }
}
