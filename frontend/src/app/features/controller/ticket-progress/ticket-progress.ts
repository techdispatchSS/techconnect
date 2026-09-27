import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { ControllerApiService } from '../controller-api.service';
import { DispatchResponseEntry, IncidentProgress, PRIORITY_LABELS } from '../controller.models';

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
  imports: [RouterLink, DatePipe],
  templateUrl: './ticket-progress.html',
  styleUrl: './ticket-progress.scss',
})
export class TicketProgress implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

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

  ngOnInit(): void {
    this.api.getIncidentProgress(this.incidentId).subscribe({
      next: (progress) => this.progress.set(progress),
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.notFound.set(true);
        } else {
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
