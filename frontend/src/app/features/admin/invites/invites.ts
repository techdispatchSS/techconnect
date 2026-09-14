import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { forkJoin } from 'rxjs';

import { AdminUserService } from '../admin-user.service';
import { AdminUser, ROLE_LABELS } from '../admin.models';
import { AdminIcon } from '../ui/admin-icon/admin-icon';

/**
 * Invites & activation: who has been added but has not yet signed in. Deliberately simpler
 * than the original mockup — the backend only ever knows PENDING_ACTIVATION vs ACTIVE, so
 * there is no "link opened" stage or expiry tracking here to make numbers up for.
 */
@Component({
  selector: 'app-invites',
  imports: [DatePipe, MatSnackBarModule, AdminIcon],
  templateUrl: './invites.html',
  styleUrl: './invites.scss',
})
export class Invites implements OnInit {
  private readonly adminUsers = inject(AdminUserService);
  private readonly snackBar = inject(MatSnackBar);

  readonly roleLabels = ROLE_LABELS;

  readonly pending = signal<AdminUser[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);

  readonly pendingCount = signal(0);
  readonly activeCount = signal(0);
  readonly totalCount = signal(0);

  readonly resendingId = signal<string | null>(null);
  readonly newInviteUrls = signal<Record<string, string>>({});

  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);

  readonly pageFrom = computed(() =>
    this.totalElements() === 0 ? 0 : this.pageIndex() * this.pageSize() + 1,
  );
  readonly pageTo = computed(() =>
    Math.min(this.totalElements(), (this.pageIndex() + 1) * this.pageSize()),
  );

  ngOnInit(): void {
    this.loadStats();
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);

    this.adminUsers
      .list({
        role: null,
        status: 'PENDING_ACTIVATION',
        q: '',
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => {
          this.pending.set(page.content);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.pending.set([]);
          this.totalElements.set(0);
          this.loading.set(false);
          this.loadFailed.set(true);
        },
      });
  }

  private loadStats(): void {
    forkJoin({
      pending: this.adminUsers.list({
        role: null,
        status: 'PENDING_ACTIVATION',
        q: '',
        page: 0,
        size: 1,
      }),
      active: this.adminUsers.list({ role: null, status: 'ACTIVE', q: '', page: 0, size: 1 }),
      total: this.adminUsers.list({ role: null, status: null, q: '', page: 0, size: 1 }),
    }).subscribe({
      next: ({ pending, active, total }) => {
        this.pendingCount.set(pending.totalElements);
        this.activeCount.set(active.totalElements);
        this.totalCount.set(total.totalElements);
      },
      error: () => undefined,
    });
  }

  onPrevPage(): void {
    if (this.pageIndex() === 0) {
      return;
    }
    this.pageIndex.update((i) => i - 1);
    this.load();
  }

  onNextPage(): void {
    if (this.pageTo() >= this.totalElements()) {
      return;
    }
    this.pageIndex.update((i) => i + 1);
    this.load();
  }

  resend(user: AdminUser): void {
    this.resendingId.set(user.id);
    this.adminUsers.resendInvite(user.id).subscribe({
      next: (response) => {
        this.resendingId.set(null);
        this.newInviteUrls.update((urls) => ({ ...urls, [user.id]: response.activationUrl }));
        this.snackBar.open(`A new activation link was issued for ${user.name}.`, 'Dismiss', {
          duration: 5000,
        });
      },
      error: (error: unknown) => {
        this.resendingId.set(null);
        const message =
          error instanceof HttpErrorResponse ? (error.error as { error?: string })?.error : null;
        this.snackBar.open(message ?? 'Could not resend the invite.', 'Dismiss', {
          duration: 6000,
        });
      },
    });
  }

  async copy(url: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(url);
      this.snackBar.open('Activation link copied.', 'Dismiss', { duration: 4000 });
    } catch {
      this.snackBar.open('Copy blocked by the browser. Select the link manually.', 'Dismiss', {
        duration: 6000,
      });
    }
  }
}
