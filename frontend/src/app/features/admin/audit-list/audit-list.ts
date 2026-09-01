import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AdminUserService } from '../admin-user.service';
import { AUDIT_ACTION_LABELS, AdminAuditAction, AuditEntry } from '../admin.models';
import { AdminIcon } from '../ui/admin-icon/admin-icon';

/**
 * Read-only view of the administrative audit trail (FR-09). There is no edit or delete
 * action anywhere here, and the API exposes none — audit records are immutable.
 *
 * <p>All searching and filtering is delegated to the server. An audit log is append-only and
 * retained for two years (§9.5), so it can never be loaded into the browser to filter there.
 *
 * <p>A `?targetUserId=&name=` deep link (from the People directory's panel) pins the real
 * `targetUserId` API filter rather than faking it through the free-text search box.
 */
@Component({
  selector: 'app-audit-list',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, AdminIcon],
  templateUrl: './audit-list.html',
  styleUrl: './audit-list.scss',
})
export class AuditList implements OnInit {
  private readonly adminUsers = inject(AdminUserService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly actions = Object.keys(AUDIT_ACTION_LABELS) as AdminAuditAction[];
  readonly actionLabels = AUDIT_ACTION_LABELS;

  readonly entries = signal<AuditEntry[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly actionFilter = signal<AdminAuditAction | null>(null);
  readonly fromDate = signal<string | null>(null);
  readonly toDate = signal<string | null>(null);

  readonly targetUserId = signal<string | null>(null);
  readonly targetUserName = signal<string | null>(null);

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.targetUserId.set(params.get('targetUserId'));
    this.targetUserName.set(params.get('name'));

    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => this.reload());

    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);

    this.adminUsers
      .audit({
        targetUserId: this.targetUserId(),
        action: this.actionFilter(),
        q: this.searchControl.value,
        from: this.fromDate(),
        to: this.toDate(),
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => {
          this.entries.set(page.content);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => {
          this.entries.set([]);
          this.totalElements.set(0);
          this.loading.set(false);
          this.loadFailed.set(true);
        },
      });
  }

  onActionFilter(value: string): void {
    this.actionFilter.set((value || null) as AdminAuditAction | null);
    this.reload();
  }

  onFromDate(value: string): void {
    this.fromDate.set(value || null);
    this.reload();
  }

  onToDate(value: string): void {
    this.toDate.set(value || null);
    this.reload();
  }

  clearTargetUser(): void {
    this.targetUserId.set(null);
    this.targetUserName.set(null);
    void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
    this.reload();
  }

  onPrevPage(): void {
    if (this.pageIndex() === 0) {
      return;
    }
    this.pageIndex.update((i) => i - 1);
    this.load();
  }

  onNextPage(): void {
    if ((this.pageIndex() + 1) * this.pageSize() >= this.totalElements()) {
      return;
    }
    this.pageIndex.update((i) => i + 1);
    this.load();
  }

  clearFilters(): void {
    this.actionFilter.set(null);
    this.fromDate.set(null);
    this.toDate.set(null);
    this.searchControl.setValue('', { emitEvent: false });
    this.clearTargetUser();
  }

  hasFilters(): boolean {
    return (
      this.actionFilter() !== null ||
      this.fromDate() !== null ||
      this.toDate() !== null ||
      this.targetUserId() !== null ||
      this.searchControl.value.trim().length > 0
    );
  }

  actionLabel(action: AdminAuditAction): string {
    return AUDIT_ACTION_LABELS[action];
  }

  private reload(): void {
    this.pageIndex.set(0);
    this.load();
  }
}
