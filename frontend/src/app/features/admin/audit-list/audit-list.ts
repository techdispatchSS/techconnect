import { DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { provideNativeDateAdapter } from '@angular/material/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { AdminUserService } from '../admin-user.service';
import { AUDIT_ACTION_LABELS, AdminAuditAction, AuditEntry } from '../admin.models';

/**
 * Read-only view of the administrative audit trail (FR-09). There is no edit or delete
 * action anywhere here, and the API exposes none — audit records are immutable.
 *
 * <p>All searching and filtering is delegated to the server. An audit log is append-only and
 * retained for two years (§9.5), so it can never be loaded into the browser to filter there.
 */
@Component({
  selector: 'app-audit-list',
  providers: [provideNativeDateAdapter()],
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
  ],
  templateUrl: './audit-list.html',
  styleUrl: './audit-list.scss',
})
export class AuditList implements OnInit {
  private readonly adminUsers = inject(AdminUserService);

  readonly displayedColumns = ['action', 'target', 'actor', 'when'] as const;

  /** Drives the action dropdown, so it can never drift from the labels map. */
  readonly actions = Object.keys(AUDIT_ACTION_LABELS) as AdminAuditAction[];

  readonly entries = signal<AuditEntry[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly actionFilter = signal<AdminAuditAction | null>(null);
  readonly fromDate = signal<Date | null>(null);
  readonly toDate = signal<Date | null>(null);

  ngOnInit(): void {
    // Debounced so typing a name does not fire a request per keystroke.
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
        targetUserId: null,
        action: this.actionFilter(),
        q: this.searchControl.value,
        from: isoDate(this.fromDate()),
        to: isoDate(this.toDate()),
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

  onActionFilter(action: AdminAuditAction | null): void {
    this.actionFilter.set(action);
    this.reload();
  }

  onFromDate(date: Date | null): void {
    this.fromDate.set(date);
    this.reload();
  }

  onToDate(date: Date | null): void {
    this.toDate.set(date);
    this.reload();
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  clearFilters(): void {
    this.actionFilter.set(null);
    this.fromDate.set(null);
    this.toDate.set(null);
    this.searchControl.setValue('', { emitEvent: false });
    this.reload();
  }

  hasFilters(): boolean {
    return (
      this.actionFilter() !== null ||
      this.fromDate() !== null ||
      this.toDate() !== null ||
      this.searchControl.value.trim().length > 0
    );
  }

  /** `*matCellDef="let entry"` is untyped, so the label is resolved through an accessor. */
  actionLabel(action: AdminAuditAction): string {
    return AUDIT_ACTION_LABELS[action];
  }

  /** Any filter change invalidates the current page offset. */
  private reload(): void {
    this.pageIndex.set(0);
    this.load();
  }
}

/** Formats as local `yyyy-MM-dd`; the API treats the range as whole days. */
function isoDate(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
