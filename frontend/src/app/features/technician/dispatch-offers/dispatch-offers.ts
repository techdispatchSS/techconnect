import { TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, interval, merge, of, startWith, switchMap } from 'rxjs';

import { priorityTone } from '../../controller/job-format';
import { RealtimeService } from '../../../core/realtime/realtime.service';
import { TechnicianApiService } from '../technician-api.service';
import { DispatchOffer } from '../technician.models';

/** New offers arrive over the socket; this beat covers it being down. */
const POLL_INTERVAL_MS = 30_000;

/** "7:05" left to answer, or null once the window has passed. */
export function timeLeft(expiresAt: string, now: number): string | null {
  const seconds = Math.floor((Date.parse(expiresAt) - now) / 1000);
  if (seconds <= 0) {
    return null;
  }
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

/**
 * Jobs offered to the technician that are waiting on their answer (FR-04), each with the time
 * left to answer and one tap to accept or decline. Emits `accepted` so the page around it can
 * pick up that the technician is now on a job.
 */
@Component({
  selector: 'app-dispatch-offers',
  imports: [TitleCasePipe],
  templateUrl: './dispatch-offers.html',
  styleUrl: './dispatch-offers.scss',
})
export class DispatchOffers {
  private readonly api = inject(TechnicianApiService);
  private readonly realtime = inject(RealtimeService);

  readonly accepted = output<DispatchOffer>();
  readonly priorityTone = priorityTone;

  private readonly all = signal<DispatchOffer[] | null>(null);
  private readonly now = signal(Date.now());
  readonly answering = signal<string | null>(null);
  readonly notice = signal<{ readonly kind: 'ok' | 'error'; readonly text: string } | null>(null);

  /** What can still be answered: one that lapses on screen drops off without a refetch. */
  readonly offers = computed(() =>
    (this.all() ?? []).filter((offer) => timeLeft(offer.expiresAt, this.now()) !== null),
  );
  readonly loaded = computed(() => this.all() !== null);

  constructor() {
    const destroyRef = inject(DestroyRef);
    merge(interval(POLL_INTERVAL_MS).pipe(startWith(0)), this.realtime.on('DISPATCH_CHANGED'))
      .pipe(
        switchMap(() => this.api.offers().pipe(catchError(() => of(null)))),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe((offers) => {
        if (offers) {
          this.all.set(offers);
        }
      });
    interval(1000)
      .pipe(takeUntilDestroyed(destroyRef))
      .subscribe(() => this.now.set(Date.now()));
  }

  left(offer: DispatchOffer): string | null {
    return timeLeft(offer.expiresAt, this.now());
  }

  refresh(): void {
    this.api.offers().subscribe({ next: (offers) => this.all.set(offers), error: () => undefined });
  }

  accept(offer: DispatchOffer): void {
    this.answer(offer, true);
  }

  decline(offer: DispatchOffer): void {
    this.answer(offer, false);
  }

  private answer(offer: DispatchOffer, accept: boolean): void {
    if (this.answering()) {
      return;
    }
    this.answering.set(offer.dispatchId);
    this.notice.set(null);
    (accept ? this.api.accept(offer.dispatchId) : this.api.decline(offer.dispatchId)).subscribe({
      next: () => {
        this.answering.set(null);
        this.all.update((all) => (all ?? []).filter((o) => o.dispatchId !== offer.dispatchId));
        this.notice.set({
          kind: 'ok',
          text: accept
            ? `You've accepted the job at ${offer.clientName}.`
            : `Declined the job at ${offer.clientName}.`,
        });
        if (accept) {
          this.accepted.emit(offer);
        }
      },
      error: (error: unknown) => {
        this.answering.set(null);
        this.notice.set({
          kind: 'error',
          text:
            (error instanceof HttpErrorResponse &&
              (error.error as { error?: string } | null)?.error) ||
            "Couldn't send your answer. Check your connection and try again.",
        });
        // Taken, lapsed or already answered: the list is stale, so show what is really left.
        this.refresh();
      },
    });
  }
}
