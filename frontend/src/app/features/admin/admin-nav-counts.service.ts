import { Injectable, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { AdminApplicationService } from './admin-application.service';
import { AdminUserService } from './admin-user.service';

/**
 * Sidebar nav badge counts (People / Invites). Cheap `size=1` requests read only
 * `totalElements` from the same paged endpoint the list screens already use — there is no
 * separate counts endpoint, and loading full pages just to count them would not scale.
 *
 * <p>A tiny shared service rather than a component input because the count can change from
 * three different screens (create, deactivate/reactivate, resend invite) and the shell that
 * renders the badges is not their parent.
 */
@Injectable({ providedIn: 'root' })
export class AdminNavCountsService {
  private readonly adminUsers = inject(AdminUserService);
  private readonly adminApplications = inject(AdminApplicationService);

  readonly peopleCount = signal<number | null>(null);
  readonly pendingInviteCount = signal<number | null>(null);
  /** Technician applications still awaiting a decision. */
  readonly pendingApplicationCount = signal<number | null>(null);
  /** True only until the first result arrives, so the badges show a loader once and then
   * quietly swap values on later refreshes instead of flickering back to a placeholder. */
  readonly loading = signal(true);

  refresh(): void {
    // Its own request: a failure here shouldn't blank the People and Invites badges, or vice versa.
    this.adminApplications.pendingCount().subscribe({
      next: (count) => this.pendingApplicationCount.set(count),
      error: () => undefined,
    });

    forkJoin({
      people: this.adminUsers.list({ role: null, status: null, q: '', page: 0, size: 1 }),
      pending: this.adminUsers.list({
        role: null,
        status: 'PENDING_ACTIVATION',
        q: '',
        page: 0,
        size: 1,
      }),
    }).subscribe({
      next: ({ people, pending }) => {
        this.peopleCount.set(people.totalElements);
        this.pendingInviteCount.set(pending.totalElements);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }
}
