import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { Address, PROVINCES, PROVINCE_LABELS, Province } from '../../../core/address.models';
import { UserRole, UserStatus } from '../../../core/auth/auth.models';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { AdminNavCountsService } from '../admin-nav-counts.service';
import { AdminUserService } from '../admin-user.service';
import { AdminUser, ROLE_LABELS, STATUS_LABELS, UserListFilters } from '../admin.models';
import { AdminIcon } from '../ui/admin-icon/admin-icon';

interface RoleTab {
  readonly label: string;
  readonly role: UserRole | null;
}

const ROLE_TABS: readonly RoleTab[] = [
  { label: 'All', role: null },
  { label: 'Technicians', role: 'TECHNICIAN' },
  { label: 'Controllers', role: 'CONTROLLER' },
  { label: 'Managers', role: 'MANAGER' },
];

/**
 * The People directory: a role-segmented table on the left and an inline edit panel on the
 * right (M-06 admin portal redesign). Selecting a row edits that person in place — there is
 * no separate detail route, so a link that needs to point at one user does it with
 * `?selected=<id>` instead (see audit-list).
 */
@Component({
  selector: 'app-user-list',
  imports: [DatePipe, ReactiveFormsModule, MatDialogModule, MatSnackBarModule, AdminIcon],
  templateUrl: './user-list.html',
  styleUrl: './user-list.scss',
})
export class UserList implements OnInit {
  private readonly adminUsers = inject(AdminUserService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly fb = inject(FormBuilder);
  private readonly counts = inject(AdminNavCountsService);

  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly roleTabs = ROLE_TABS;
  readonly provinces = PROVINCES;
  readonly provinceLabels = PROVINCE_LABELS;

  readonly users = signal<AdminUser[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loadFailed = signal(false);

  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly roleFilter = signal<UserRole | null>(null);
  readonly statusFilter = signal<UserStatus | null>(null);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);

  readonly selected = signal<AdminUser | null>(null);
  readonly selectedLoading = signal(false);
  readonly panelSaving = signal(false);
  readonly newInviteUrl = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    address: this.fb.nonNullable.group({
      street: ['', [Validators.required, Validators.maxLength(255)]],
      suburb: ['', [Validators.required, Validators.maxLength(120)]],
      city: ['', [Validators.required, Validators.maxLength(120)]],
      province: ['' as Province | '', [Validators.required]],
      postalCode: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    }),
    role: ['TECHNICIAN' as UserRole, [Validators.required]],
  });

  readonly resultLine = computed(() => {
    const n = this.totalElements();
    return `${n} ${n === 1 ? 'person' : 'people'} · sorted by newest`;
  });

  readonly pageFrom = computed(() =>
    this.totalElements() === 0 ? 0 : this.pageIndex() * this.pageSize() + 1,
  );
  readonly pageTo = computed(() =>
    Math.min(this.totalElements(), (this.pageIndex() + 1) * this.pageSize()),
  );

  ngOnInit(): void {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged())
      .subscribe(() => {
        this.pageIndex.set(0);
        this.load();
      });

    const preselect = this.route.snapshot.queryParamMap.get('selected');
    this.load(preselect);
  }

  load(preselectId?: string | null): void {
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
        this.afterLoad(page.content, preselectId);
      },
      error: () => {
        this.users.set([]);
        this.totalElements.set(0);
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  /** Picks up a `?selected=` deep link, or otherwise keeps the current selection — falling
   * back to the first row only when nothing is selected yet. */
  private afterLoad(rows: AdminUser[], preselectId?: string | null): void {
    if (preselectId) {
      const inPage = rows.find((r) => r.id === preselectId);
      if (inPage) {
        this.select(inPage);
      } else {
        this.selectedLoading.set(true);
        this.adminUsers.get(preselectId).subscribe({
          next: (user) => this.select(user),
          error: () => this.selectedLoading.set(false),
        });
      }
      return;
    }

    if (!this.selected() && rows.length > 0) {
      this.select(rows[0]);
    }
  }

  onRoleTab(role: UserRole | null): void {
    this.roleFilter.set(role);
    this.pageIndex.set(0);
    this.load();
  }

  onStatusFilter(value: string): void {
    this.statusFilter.set((value || null) as UserStatus | null);
    this.pageIndex.set(0);
    this.load();
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

  hasFilters(): boolean {
    return (
      this.roleFilter() !== null ||
      this.statusFilter() !== null ||
      this.searchControl.value.trim().length > 0
    );
  }

  select(user: AdminUser): void {
    this.selectedLoading.set(false);
    this.selected.set(user);
    this.newInviteUrl.set(null);
    this.form.reset({
      name: user.name,
      phone: user.phone ?? '',
      address: this.addressFormValue(user.address),
      role: user.role,
    });

    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { selected: user.id },
      replaceUrl: true,
    });
  }

  cancelEdit(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    this.form.reset({
      name: user.name,
      phone: user.phone ?? '',
      address: this.addressFormValue(user.address),
      role: user.role,
    });
  }

  /** The bootstrap Manager is the only account that can reach this panel with no address at
   * all — everyone else was required to have one at onboarding. Falls back to blank fields
   * rather than failing to open the panel, so a Manager can fill it in here instead. */
  private addressFormValue(address: Address | null) {
    return {
      street: address?.street ?? '',
      suburb: address?.suburb ?? '',
      city: address?.city ?? '',
      province: (address?.province ?? '') as Province | '',
      postalCode: address?.postalCode ?? '',
    };
  }

  save(): void {
    const user = this.selected();
    if (!user || this.form.invalid || this.panelSaving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.panelSaving.set(true);
    const { name, phone, address, role } = this.form.getRawValue();

    this.adminUsers
      .update(user.id, {
        name,
        phone: phone.trim() || null,
        address: { ...address, province: address.province as Province },
        role,
      })
      .subscribe({
        next: (updated) => {
          this.panelSaving.set(false);
          this.applyUpdate(updated);
          this.snackBar.open('Changes saved.', 'Dismiss', { duration: 4000 });
        },
        error: (error: unknown) => {
          this.panelSaving.set(false);
          this.snackBar.open(this.messageFor(error, 'Could not save changes.'), 'Dismiss', {
            duration: 6000,
          });
        },
      });
  }

  deactivate(): void {
    const user = this.selected();
    if (!user) {
      return;
    }

    const data: ConfirmDialogData = {
      title: `Deactivate ${user.name}?`,
      message:
        'They will be signed out immediately and will not be able to sign in again. Their job ' +
        'history and audit trail are kept. You can reactivate them later.',
      confirmLabel: 'Deactivate',
      destructive: true,
    };

    this.dialog
      .open(ConfirmDialog, { data, width: '28rem', panelClass: 'admin-confirm-dialog' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.panelSaving.set(true);
        this.adminUsers.deactivate(user.id).subscribe({
          next: (updated) => this.afterStatusChange(updated, `${updated.name} deactivated.`),
          error: (error: unknown) => this.afterStatusError(error, 'Could not deactivate.'),
        });
      });
  }

  reactivate(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    this.panelSaving.set(true);
    this.adminUsers.reactivate(user.id).subscribe({
      next: (updated) => this.afterStatusChange(updated, `${updated.name} reactivated.`),
      error: (error: unknown) => this.afterStatusError(error, 'Could not reactivate.'),
    });
  }

  resendInvite(): void {
    const user = this.selected();
    if (!user) {
      return;
    }
    this.panelSaving.set(true);
    this.adminUsers.resendInvite(user.id).subscribe({
      next: (response) => {
        this.panelSaving.set(false);
        this.newInviteUrl.set(response.activationUrl);
        this.snackBar.open('A new activation link has been issued.', 'Dismiss', { duration: 5000 });
      },
      error: (error: unknown) => {
        this.panelSaving.set(false);
        this.snackBar.open(this.messageFor(error, 'Could not resend the invite.'), 'Dismiss', {
          duration: 6000,
        });
      },
    });
  }

  async copyInviteUrl(): Promise<void> {
    const url = this.newInviteUrl();
    if (!url) {
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      this.snackBar.open('Activation link copied.', 'Dismiss', { duration: 4000 });
    } catch {
      this.snackBar.open('Copy blocked by the browser. Select the link manually.', 'Dismiss', {
        duration: 6000,
      });
    }
  }

  initials(name: string): string {
    const parts = name.trim().split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
    return (first + last).toUpperCase();
  }

  statusTagClass(user: AdminUser): string {
    switch (user.status) {
      case 'ACTIVE':
        return 'admin-tag admin-tag-active';
      case 'PENDING_ACTIVATION':
        return 'admin-tag admin-tag-pending';
      default:
        return 'admin-tag admin-tag-disabled';
    }
  }

  private applyUpdate(updated: AdminUser): void {
    this.users.update((rows) => rows.map((r) => (r.id === updated.id ? updated : r)));
    this.selected.set(updated);
  }

  private afterStatusChange(updated: AdminUser, message: string): void {
    this.panelSaving.set(false);
    this.newInviteUrl.set(null);
    this.applyUpdate(updated);
    this.snackBar.open(message, 'Dismiss', { duration: 4000 });
    this.counts.refresh();
  }

  private afterStatusError(error: unknown, fallback: string): void {
    this.panelSaving.set(false);
    this.snackBar.open(this.messageFor(error, fallback), 'Dismiss', { duration: 6000 });
  }

  private messageFor(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      const apiMessage = (error.error as { error?: string } | null)?.error;
      if (apiMessage) {
        return apiMessage;
      }
    }
    return fallback;
  }
}
