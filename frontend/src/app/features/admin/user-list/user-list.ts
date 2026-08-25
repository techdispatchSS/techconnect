import { Component, OnInit, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { UserRole, UserStatus } from '../../../core/auth/auth.models';
import { AdminUserService } from '../admin-user.service';
import { AdminUser, ROLE_LABELS, STATUS_LABELS, UserListFilters } from '../admin.models';
import { UserCreateDialog } from '../user-create-dialog/user-create-dialog';

@Component({
  selector: 'app-user-list',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatChipsModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatPaginatorModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    MatTooltipModule,
  ],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList implements OnInit {
  private readonly adminUsers = inject(AdminUserService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);

  readonly displayedColumns = ['name', 'role', 'status', 'phone', 'actions'] as const;

  readonly users = signal<AdminUser[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly roleFilter = signal<UserRole | null>(null);
  readonly statusFilter = signal<UserStatus | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);

  ngOnInit(): void {
    // Debounced so typing a name does not fire a request per keystroke.
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.load();
      });

    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);

    const filters: UserListFilters = {
      role: this.roleFilter(),
      status: this.statusFilter(),
      q: this.searchControl.value,
      page: this.pageIndex(),
      size: this.pageSize(),
    };

    this.adminUsers.list(filters).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.totalElements.set(page.totalElements);
        this.loading.set(false);
      },
      error: () => {
        this.users.set([]);
        this.totalElements.set(0);
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  onRoleFilter(role: UserRole | null): void {
    this.roleFilter.set(role);
    this.pageIndex.set(0);
    this.load();
  }

  onStatusFilter(status: UserStatus | null): void {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
    this.load();
  }

  onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.load();
  }

  clearFilters(): void {
    this.roleFilter.set(null);
    this.statusFilter.set(null);
    this.searchControl.setValue('', { emitEvent: false });
    this.pageIndex.set(0);
    this.load();
  }

  hasFilters(): boolean {
    return (
      this.roleFilter() !== null ||
      this.statusFilter() !== null ||
      this.searchControl.value.trim().length > 0
    );
  }

  addUser(): void {
    this.dialog
      .open(UserCreateDialog, { width: '32rem', disableClose: true })
      .afterClosed()
      .subscribe((created) => {
        if (created) {
          this.pageIndex.set(0);
          this.load();
        }
      });
  }

  openUser(user: AdminUser): void {
    void this.router.navigate(['/admin/users', user.id]);
  }

  // `*matCellDef="let user"` is untyped, so labels are resolved through these accessors
  // rather than by indexing a Record with an `any` in the template.
  roleLabel(role: UserRole): string {
    return ROLE_LABELS[role];
  }

  statusLabel(status: UserStatus): string {
    return STATUS_LABELS[status];
  }

  /** Drives the status chip colour; deactivated accounts must be obvious at a glance. */
  statusAppearance(user: AdminUser): 'active' | 'pending' | 'disabled' {
    switch (user.status) {
      case 'ACTIVE':
        return 'active';
      case 'PENDING_ACTIVATION':
        return 'pending';
      default:
        return 'disabled';
    }
  }
}
