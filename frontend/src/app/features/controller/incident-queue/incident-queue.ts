import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, interval, startWith } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import {
  Incident,
  IncidentKpis,
  IncidentPriority,
  PRIORITY_LABELS,
  QUEUE_TABS,
  QueueTab,
} from '../controller.models';
import {
  DispatchState,
  INCIDENT_STATUS_LABELS,
  canResend,
  dispatchState,
  needsNewDispatch,
  timeLeft,
} from '../dispatch-state';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { NewIncidentNotifierService } from '../new-incident-notifier.service';

/** Matches the design mockup's auto-refresh cadence for the incident queue. */
const REFRESH_INTERVAL_MS = 30_000;
const PAGE_SIZE = 20;

@Component({
  selector: 'app-incident-queue',
  imports: [DatePipe, ReactiveFormsModule, RouterLink],
  templateUrl: './incident-queue.html',
  styleUrl: './incident-queue.scss',
})
export class IncidentQueue implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly notifier = inject(NewIncidentNotifierService);
  private readonly freshdesk = inject(FreshdeskStatusService);

  /** A `?selected=` deep link from a new-incident toast (see `ControllerShell.openAlert`).
   * Tracked outside the Router's own snapshot because clicking a toast while already on this
   * route reuses the existing component instance rather than re-running `ngOnInit` — only a
   * live subscription to `queryParamMap` (below) sees that kind of same-route navigation. */
  private preselectId: string | null = null;
  private lastAppliedPreselectId: string | null = null;
  private loadSeq = 0;

  readonly tabs = QUEUE_TABS;
  readonly priorityLabels = PRIORITY_LABELS;
  readonly priorities: IncidentPriority[] = ['HIGH', 'MEDIUM', 'LOW'];

  readonly activeTab = signal<QueueTab>('New');
  readonly searchControl = new FormControl('', { nonNullable: true });
  /** '' = every priority. */
  readonly priorityFilter = signal<IncidentPriority | ''>('');

  readonly incidents = signal<Incident[]>([]);
  readonly kpis = signal<IncidentKpis | null>(null);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly selectedId = signal<string | null>(null);

  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalElements = signal(0);

  /** The demo-only controls (raise a simulated Freshdesk ticket) apply only when the backend
   * says simulation is on; they are absent, not disabled, in a real deployment. */
  readonly simulation = computed(() => this.freshdesk.status()?.simulation === true);
  readonly simulating = signal(false);

  /** The dispatch currently being re-notified, so only its button shows as busy. */
  readonly resending = signal<string | null>(null);
  readonly resendFeedback = signal<{ ok: boolean; text: string } | null>(null);
  readonly simulationError = signal<string | null>(null);

  /** The deep-linked incident, fetched on its own: it is sorted by SLA like everything else, so
   * it may sit on a page other than the one loaded (a Medium incident logged just now lands
   * behind the urgent ones). Without this the panel would fall back to the first row. */
  private readonly pinned = signal<Incident | null>(null);

  readonly selected = computed<Incident | null>(() => {
    const id = this.selectedId();
    const list = this.incidents();
    const pinned = this.pinned();
    return (
      list.find((incident) => incident.id === id) ??
      (pinned && pinned.id === id ? pinned : null) ??
      (list.length > 0 ? list[0] : null)
    );
  });

  /** The panel is showing an incident that is not among the rows currently listed. */
  readonly selectedOffPage = computed(() => {
    const selected = this.selected();
    return selected !== null && !this.incidents().some((incident) => incident.id === selected.id);
  });

  readonly tabCount = computed(() => {
    const kpis = this.kpis();
    if (!kpis) {
      return {} as Record<QueueTab, number | null>;
    }
    return {
      New: kpis.newCount,
      Unassigned: kpis.unassignedCount,
      'In progress': kpis.inProgressCount,
      Overdue: kpis.overdueCount,
    } satisfies Record<QueueTab, number>;
  });

  readonly rangeLabel = computed(() => {
    const total = this.totalElements();
    if (total === 0) {
      return '';
    }
    const from = this.page() * PAGE_SIZE + 1;
    const to = Math.min(total, from + this.incidents().length - 1);
    return `${from}–${to} of ${total}`;
  });

  ngOnInit(): void {
    // Fires with the current value immediately on subscribe, and again on every later
    // same-route navigation — including a toast click while the queue is already open,
    // which `ActivatedRoute.snapshot` alone would miss.
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const id = params.get('selected');
      if (!id || id === this.lastAppliedPreselectId) {
        return;
      }
      const alreadyLoaded = this.incidents().find((incident) => incident.id === id);
      if (alreadyLoaded) {
        this.lastAppliedPreselectId = id;
        this.selectedId.set(id);
        return;
      }
      // Not in what's currently loaded — either this is the first navigation into the page
      // (nothing loaded yet) or a different tab is showing. A deep link can be for a NEW
      // incident (a toast, one just logged) or for one that has just been dispatched and so
      // left the New tab; ask which, and open the tab it is in. Otherwise a ticket that was
      // just sent would vanish and the panel would show the next one's "Create dispatch".
      this.preselectId = id;
      const open = (tab: QueueTab): void => {
        this.activeTab.set(tab);
        this.page.set(0);
        this.load();
      };
      this.api.getIncident(id).subscribe({
        next: (incident) => {
          // Selected now rather than when the list arrives: it may well be on a later page, and
          // waiting for it to show up in the rows would leave the panel on the wrong ticket.
          this.lastAppliedPreselectId = id;
          this.pinned.set(incident);
          this.selectedId.set(id);
          open(incident.status === 'IN_PROGRESS' ? 'In progress' : 'New');
        },
        error: () => open('New'),
      });
    });

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.firstPage());

    // Auto-refresh: the controller dashboard is a live operational queue, not a page you
    // reload — `startWith(0)` fires the first load immediately, then every 30s after. The
    // counts ride along so they don't go stale while the list moves.
    interval(REFRESH_INTERVAL_MS)
      .pipe(startWith(0), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.load(true);
        this.loadKpis();
      });
  }

  selectTab(tab: QueueTab): void {
    this.activeTab.set(tab);
    this.firstPage();
  }

  setPriorityFilter(value: string): void {
    this.priorityFilter.set(value as IncidentPriority | '');
    this.firstPage();
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.page.update((p) => p - 1);
      this.load();
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.page.update((p) => p + 1);
      this.load();
    }
  }

  select(incident: Incident): void {
    // A message about one ticket's resend doesn't belong under another.
    this.resendFeedback.set(null);
    this.pinned.set(null);
    this.selectedId.set(incident.id);
  }

  /** "Create dispatch" until one has gone out, "View progress" after — decided by whether a
   * dispatch actually exists, not by status: an OVERDUE incident can be either, and sending
   * one without a dispatch to the progress page would only 404. */
  openIncident(): void {
    const incident = this.selected();
    if (!incident) {
      return;
    }
    // A dispatch still out, or already taken, is followed on the progress page; one that
    // lapsed (or was refused by everyone) needs a fresh dispatch rather than a status page.
    const segment = needsNewDispatch(incident.dispatch) ? 'dispatch' : 'progress';
    this.router.navigate(['/incidents', segment, incident.id]);
  }

  actionLabel(incident: Incident): string {
    if (!incident.dispatch) {
      return 'Create dispatch';
    }
    return needsNewDispatch(incident.dispatch) ? 'Dispatch again' : 'View dispatch status';
  }

  stateOf(incident: Incident): DispatchState {
    return dispatchState(incident.dispatch);
  }

  /** Colours the row chip: waiting is the live state, so it is the one that stands out. */
  chipClass(incident: Incident): string {
    return `iq-dispatch-chip iq-dispatch-chip--${this.stateOf(incident).kind}`;
  }

  timeLeftOf(incident: Incident): string | null {
    return timeLeft(incident.dispatch);
  }

  canResend(incident: Incident): boolean {
    return canResend(incident.dispatch);
  }

  statusLabel(incident: Incident): string {
    return INCIDENT_STATUS_LABELS[incident.status];
  }

  /** "Notified twice, last at 21:04" — what the Controller needs to decide whether to nudge. */
  notifiedLabel(incident: Incident): string | null {
    const summary = incident.dispatch;
    if (!summary || summary.notificationCount === 0 || !summary.lastNotifiedAt) {
      return null;
    }
    const times = summary.notificationCount === 1 ? 'once' : `${summary.notificationCount} times`;
    const at = new Date(summary.lastNotifiedAt).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
    return `Notified ${times}, last at ${at}`;
  }

  /** Sends the job notice again to the technicians who haven't answered. */
  resend(incident: Incident): void {
    const summary = incident.dispatch;
    if (!summary || this.resending()) {
      return;
    }
    this.resending.set(summary.id);
    this.resendFeedback.set(null);

    this.api.resendDispatch(summary.id).subscribe({
      next: (result) => {
        this.resending.set(null);
        this.resendFeedback.set({
          ok: true,
          text: `Notification sent again to ${result.notified} technician${result.notified === 1 ? '' : 's'}.`,
        });
        this.load(true);
      },
      error: (error: unknown) => {
        this.resending.set(null);
        const server =
          error instanceof HttpErrorResponse
            ? (error.error as { error?: string } | null)?.error
            : null;
        this.resendFeedback.set({ ok: false, text: server ?? "Couldn't resend the notification." });
        // Something changed under us (answered, expired) — show the truth, not a stale button.
        this.load(true);
      },
    });
  }

  /** Demo only: raises a ticket in the simulated Freshdesk, which then arrives through the
   * same sync a live one would. The queue is re-read at once so it shows up without waiting. */
  simulateTicket(overdue: boolean): void {
    if (this.simulating()) {
      return;
    }
    this.simulating.set(true);
    this.simulationError.set(null);
    this.api.simulateFreshdeskTicket(overdue).subscribe({
      next: () => {
        this.simulating.set(false);
        this.activeTab.set(overdue ? 'Overdue' : 'New');
        this.page.set(0);
        this.load();
        this.loadKpis();
        this.freshdesk.refresh();
        this.notifier.refresh();
      },
      error: () => {
        this.simulating.set(false);
        this.simulationError.set("Couldn't raise a simulated ticket.");
      },
    });
  }

  /** Minutes since an incident was logged, matching the queue's "Age" column. */
  ageLabel(incident: Incident): string {
    const minutes = Math.max(
      0,
      Math.round((Date.now() - new Date(incident.createdAt).getTime()) / 60_000),
    );
    if (minutes < 60) {
      return `${minutes}m`;
    }
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    return remainder === 0 ? `${hours}h` : `${hours}h ${remainder}m`;
  }

  /** SLA countdown for the detail panel — "Overdue by 20m" or "3h 55m remaining". */
  slaCountdown(incident: Incident): string | null {
    if (!incident.slaDueAt) {
      return null;
    }
    const remainingMs = new Date(incident.slaDueAt).getTime() - Date.now();
    const minutes = Math.round(Math.abs(remainingMs) / 60_000);
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    const duration = hours > 0 ? `${hours}h ${remainder}m` : `${remainder}m`;
    return remainingMs < 0 ? `Overdue by ${duration}` : `${duration} remaining`;
  }

  /** Elapsed fraction of the SLA window, for the progress bar — real, not fabricated: both
   * endpoints of the window (`createdAt`, `slaDueAt`) come straight from the incident. */
  slaProgressPct(incident: Incident): number {
    if (!incident.slaDueAt) {
      return 0;
    }
    const start = new Date(incident.createdAt).getTime();
    const due = new Date(incident.slaDueAt).getTime();
    const total = due - start;
    if (total <= 0) {
      return 100;
    }
    const elapsed = Date.now() - start;
    return Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  }

  priorityClass(incident: Incident): string {
    switch (incident.priority) {
      case 'HIGH':
        return 'controller-pill controller-pill--high';
      case 'MEDIUM':
        return 'controller-pill controller-pill--medium';
      default:
        return 'controller-pill controller-pill--low';
    }
  }

  /** A new filter, search or tab starts again from the top of the list. */
  private firstPage(): void {
    // A different view: the incident that was deep-linked no longer belongs in the panel.
    this.pinned.set(null);
    this.page.set(0);
    this.load();
  }

  private loadKpis(): void {
    this.api.kpis().subscribe({ next: (kpis) => this.kpis.set(kpis), error: () => undefined });
  }

  /** The pinned incident is not in the listed rows, so the refresh that keeps those current
   * would otherwise leave it showing a dispatch state that has since moved on. */
  private refreshPinned(rows: Incident[]): void {
    const pinned = this.pinned();
    if (!pinned || rows.some((incident) => incident.id === pinned.id)) {
      return;
    }
    this.api.getIncident(pinned.id).subscribe({
      next: (fresh) => {
        if (this.pinned()?.id === fresh.id) {
          this.pinned.set(fresh);
        }
      },
      error: () => undefined,
    });
  }

  /** `silent` is the background refresh: it swaps the rows in place rather than blanking the
   * table to "Loading…" every 30 seconds under someone who is reading it. */
  private load(silent = false): void {
    if (!silent) {
      this.loading.set(true);
    }
    this.loadFailed.set(false);

    // A slower, older response (the background refresh, say) must not overwrite the rows of a
    // newer request the person has since made by changing tab, filter or page.
    const seq = ++this.loadSeq;
    const tab = this.activeTab();
    this.api
      .listIncidents({
        status:
          tab === 'New'
            ? 'NEW'
            : tab === 'In progress'
              ? 'IN_PROGRESS'
              : tab === 'Overdue'
                ? 'OVERDUE'
                : undefined,
        priority: this.priorityFilter() || undefined,
        unassigned: tab === 'Unassigned' ? true : undefined,
        q: this.searchControl.value,
        page: this.page(),
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (page) => {
          if (seq !== this.loadSeq) {
            return;
          }
          // The list shrank under us (incidents left this view since the last refresh) and this
          // page no longer exists — step back rather than showing an empty page of a full list.
          if (page.content.length === 0 && this.page() > 0 && page.totalPages > 0) {
            this.page.set(page.totalPages - 1);
            this.load();
            return;
          }

          this.incidents.set(page.content);
          this.totalPages.set(page.totalPages);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
          if (silent) {
            this.refreshPinned(page.content);
          }

          if (this.preselectId && this.preselectId !== this.lastAppliedPreselectId) {
            const match = page.content.find((incident) => incident.id === this.preselectId);
            if (match) {
              this.lastAppliedPreselectId = this.preselectId;
              this.selectedId.set(this.preselectId);
              return;
            }
          }
          if (!this.selectedId() && page.content.length > 0) {
            this.selectedId.set(page.content[0].id);
          }
        },
        error: () => {
          if (seq !== this.loadSeq) {
            return;
          }
          this.loading.set(false);
          this.loadFailed.set(true);
        },
      });
  }
}
