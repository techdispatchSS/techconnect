import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { interval, startWith, switchMap } from 'rxjs';

import { ControllerApiService } from './controller-api.service';
import { Incident } from './controller.models';

export interface IncidentAlert {
  readonly id: string;
  readonly incident: Incident;
}

/** Matches the incident queue's own auto-refresh cadence. */
const POLL_INTERVAL_MS = 30_000;
/** How long a toast sits before it clears itself, absent a hover. */
const AUTO_DISMISS_MS = 12_000;

/**
 * Watches for incidents that arrive as NEW while a Controller is signed in and surfaces them
 * as dismissible, clickable toasts — wherever in the dashboard they currently are, not only
 * while the queue's "New" tab happens to be open. Also the single source of the sidebar's
 * "Incident queue" badge, so that count updates live instead of only once at sign-in.
 *
 * <p>Started once from `ControllerShell`, which stays mounted for the Controller's whole
 * session. A root-scoped service outlives one shell instance (e.g. sign out, then a different
 * Controller signs in on the same tab), so {@link start} resets all state on every call rather
 * than latching — each new shell legitimately gets a fresh poll and an empty toast tray.
 */
@Injectable({ providedIn: 'root' })
export class NewIncidentNotifierService {
  private readonly api = inject(ControllerApiService);

  readonly newCount = signal<number | null>(null);
  readonly alerts = signal<IncidentAlert[]>([]);

  private knownIds = new Set<string>();
  /** False until the first poll lands, so incidents already NEW when the Controller signs in
   * are counted but not announced — only arrivals after that get a toast. */
  private seeded = false;
  private readonly dismissTimers = new Map<string, ReturnType<typeof setTimeout>>();

  start(destroyRef: DestroyRef): void {
    this.clearDismissTimers();
    this.knownIds = new Set();
    this.seeded = false;
    this.alerts.set([]);
    this.newCount.set(null);

    interval(POLL_INTERVAL_MS)
      .pipe(
        startWith(0),
        switchMap(() => this.api.listIncidents({ status: 'NEW', q: '', page: 0, size: 50 })),
        takeUntilDestroyed(destroyRef),
      )
      .subscribe({
        next: (page) => this.reconcile(page.content, page.totalElements),
        error: () => undefined,
      });

    destroyRef.onDestroy(() => this.clearDismissTimers());
  }

  dismiss(id: string): void {
    this.alerts.update((list) => list.filter((a) => a.id !== id));
    this.cancelAutoDismiss(id);
  }

  /** Pauses the auto-dismiss clock while the pointer is over a toast — closing one out from
   * under the Controller mid-read is worse than one that lingers a little. */
  cancelAutoDismiss(id: string): void {
    const timer = this.dismissTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.dismissTimers.delete(id);
    }
  }

  scheduleAutoDismiss(id: string): void {
    this.cancelAutoDismiss(id);
    this.dismissTimers.set(
      id,
      setTimeout(() => this.dismiss(id), AUTO_DISMISS_MS),
    );
  }

  private reconcile(incidents: Incident[], totalElements: number): void {
    this.newCount.set(totalElements);

    const currentIds = new Set(incidents.map((i) => i.id));

    if (!this.seeded) {
      this.knownIds = currentIds;
      this.seeded = true;
      return;
    }

    const arrivals = incidents.filter((i) => !this.knownIds.has(i.id));
    this.knownIds = currentIds;
    if (arrivals.length === 0) {
      return;
    }

    const newAlerts = arrivals.map((incident) => ({ id: incident.id, incident }));
    this.alerts.update((list) => [...newAlerts, ...list]);
    for (const alert of newAlerts) {
      this.scheduleAutoDismiss(alert.id);
    }
  }

  private clearDismissTimers(): void {
    for (const timer of this.dismissTimers.values()) {
      clearTimeout(timer);
    }
    this.dismissTimers.clear();
  }
}
