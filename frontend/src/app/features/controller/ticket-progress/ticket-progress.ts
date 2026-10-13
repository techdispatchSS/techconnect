import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { DispatchResponseEntry, IncidentProgress, PRIORITY_LABELS } from '../controller.models';
import { canResend, dispatchState } from '../dispatch-state';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { Skeleton } from '../../../shared/skeleton/skeleton';

/** How often a still-open dispatch is re-read. Short: an accept is what the Controller is
 * waiting on, and the queue's own 30s would feel dead here. */
const REFRESH_INTERVAL_MS = 10_000;

/**
 * The controller's view of an incident that's already in progress — what was dispatched, and
 * how every invited technician has responded. Reached from the queue instead of "Create
 * dispatch" once an incident is past `NEW` (see `IncidentQueue.openIncident`).
 *
 * <p>There is no on-site job timeline (en route / on site / work complete) here on purpose —
 * the technician app that would produce those events doesn't exist yet (see
 * `IncidentProgressResponse` on the backend), so there's nothing real to show beyond dispatch
 * and response state.
 */
@Component({
  selector: 'app-ticket-progress',
  imports: [RouterLink, DatePipe, Skeleton],
  templateUrl: './ticket-progress.html',
  styleUrl: './ticket-progress.scss',
})
export class TicketProgress implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly freshdesk = inject(FreshdeskStatusService);

  readonly priorityLabels = PRIORITY_LABELS;

  private readonly incidentId = this.route.snapshot.paramMap.get('incidentId')!;

  readonly progress = signal<IncidentProgress | null>(null);
  readonly loadFailed = signal(false);
  readonly notFound = signal(false);

  readonly sortedResponses = computed(() => {
    const p = this.progress();
    if (!p) {
      return [];
    }
    // Accepted first, then pending, then declined/no-response — the order a controller
    // actually cares about, not insertion order.
    const rank: Record<DispatchResponseEntry['response'], number> = {
      ACCEPTED: 0,
      PENDING: 1,
      DECLINED: 2,
      NO_RESPONSE: 3,
    };
    return [...p.responses].sort((a, b) => rank[a.response] - rank[b.response]);
  });

  /** What the dispatch looks like to a Controller: how many it went to, who is still to answer. */
  readonly state = computed(() => dispatchState(this.progress()?.incident.dispatch ?? null));
  readonly resendable = computed(() => canResend(this.progress()?.incident.dispatch ?? null));
  readonly resending = signal(false);
  readonly resendFeedback = signal<{ ok: boolean; text: string } | null>(null);

  readonly notifiedLabel = computed(() => {
    const summary = this.progress()?.incident.dispatch;
    if (!summary || summary.notificationCount === 0 || !summary.lastNotifiedAt) {
      return null;
    }
    const times = summary.notificationCount === 1 ? 'once' : `${summary.notificationCount} times`;
    const at = new Date(summary.lastNotifiedAt).toLocaleTimeString([], {
      hour: '2-digit',
      minute: '2-digit',
    });
    return `Technicians notified ${times}, last at ${at}`;
  });

  /** Sends the job notice again to whoever hasn't answered; the window itself is unchanged. */
  resend(): void {
    const p = this.progress();
    if (!p || this.resending()) {
      return;
    }
    this.resending.set(true);
    this.resendFeedback.set(null);
    this.api.resendDispatch(p.dispatchId).subscribe({
      next: (result) => {
        this.resending.set(false);
        this.resendFeedback.set({
          ok: true,
          text: `Notification sent again to ${result.notified} technician${result.notified === 1 ? '' : 's'}.`,
        });
        this.load();
      },
      error: (error: unknown) => {
        this.resending.set(false);
        const server =
          error instanceof HttpErrorResponse
            ? (error.error as { error?: string } | null)?.error
            : null;
        this.resendFeedback.set({ ok: false, text: server ?? "Couldn't resend the notification." });
        this.load();
      },
    });
  }

  /** Every invitee has declined — worth saying differently from a window that simply ran out. */
  readonly allDeclined = computed(() => {
    const p = this.progress();
    return !!p && p.responses.length > 0 && p.responses.every((r) => r.response === 'DECLINED');
  });

  /** Demo controls (answer as a technician) only exist when the backend says simulation is on. */
  readonly simulation = computed(() => this.freshdesk.status()?.simulation === true);
  readonly answering = signal<string | null>(null);
  readonly answerError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();

    // While nobody has answered yet this is a live view: pick up an accept, a decline or the
    // window lapsing without a reload. Once the dispatch is decided there is nothing left to
    // wait for, so it stops asking.
    interval(REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.progress()?.dispatchStatus === 'PENDING') {
          this.load();
        }
      });
  }

  /** Demo only: answers this dispatch as one of the technicians it was sent to. */
  answerAs(entry: DispatchResponseEntry, response: 'ACCEPTED' | 'DECLINED'): void {
    const p = this.progress();
    if (!p || this.answering()) {
      return;
    }
    this.answering.set(entry.technicianId);
    this.answerError.set(null);
    this.api.simulateTechnicianResponse(p.dispatchId, entry.technicianId, response).subscribe({
      next: () => {
        this.answering.set(null);
        this.load();
      },
      error: (error: unknown) => {
        this.answering.set(null);
        const server =
          error instanceof HttpErrorResponse
            ? (error.error as { error?: string } | null)?.error
            : null;
        this.answerError.set(server ?? "Couldn't record that answer.");
        this.load();
      },
    });
  }

  private load(): void {
    this.api.getIncidentProgress(this.incidentId).subscribe({
      next: (progress) => {
        this.progress.set(progress);
        this.loadFailed.set(false);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.notFound.set(true);
        } else if (!this.progress()) {
          // A failed background refresh keeps showing what we had rather than blanking it.
          this.loadFailed.set(true);
        }
      },
    });
  }

  /** Minutes until the dispatch's response window lapses — negative once it's expired. */
  private minutesToExpiry(p: IncidentProgress): number {
    return Math.round((new Date(p.dispatchExpiresAt).getTime() - Date.now()) / 60_000);
  }

  /** "Expires in 6m" while pending, "Expired 4m ago" once the window has passed. */
  expiryLabel(): string | null {
    const p = this.progress();
    if (!p || p.dispatchStatus !== 'PENDING') {
      return null;
    }
    const minutes = this.minutesToExpiry(p);
    return minutes >= 0 ? `Expires in ${minutes}m` : `Expired ${Math.abs(minutes)}m ago`;
  }

  /** Elapsed fraction of the broadcast's 10-minute response window, for the countdown bar. */
  expiryProgressPct(): number {
    const p = this.progress();
    if (!p) {
      return 0;
    }
    const total = new Date(p.dispatchExpiresAt).getTime() - new Date(p.dispatchCreatedAt).getTime();
    if (total <= 0) {
      return 100;
    }
    const elapsed = Date.now() - new Date(p.dispatchCreatedAt).getTime();
    return Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  }

  responseLabel(response: DispatchResponseEntry['response']): string {
    switch (response) {
      case 'ACCEPTED':
        return 'Accepted';
      case 'DECLINED':
        return 'Declined';
      case 'NO_RESPONSE':
        return 'No response';
      default:
        return 'Pending';
    }
  }

  responsePillClass(response: DispatchResponseEntry['response']): string {
    switch (response) {
      case 'ACCEPTED':
        return 'controller-pill controller-pill--medium';
      case 'DECLINED':
      case 'NO_RESPONSE':
        return 'controller-pill controller-pill--low';
      default:
        return 'controller-pill controller-pill--high';
    }
  }

  dispatchAgain(): void {
    const p = this.progress();
    if (p) {
      void this.router.navigate(['/incidents/dispatch', p.incident.id]);
    }
  }
}
