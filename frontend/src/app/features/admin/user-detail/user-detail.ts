import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { UserRole } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { ConfirmDialog, ConfirmDialogData } from '../../../shared/confirm-dialog/confirm-dialog';
import { AdminUserService } from '../admin-user.service';
import {
  AUDIT_ACTION_LABELS,
  AdminUser,
  AuditEntry,
  ROLE_LABELS,
  STATUS_LABELS,
} from '../admin.models';

@Component({
  selector: 'app-user-detail',
  imports: [
    DatePipe,
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './user-detail.html',
  styleUrl: './user-detail.scss',
})
export class UserDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly adminUsers = inject(AdminUserService);
  private readonly auth = inject(AuthService);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly fb = inject(FormBuilder);

  readonly roleLabels = ROLE_LABELS;
  readonly statusLabels = STATUS_LABELS;
  readonly auditLabels = AUDIT_ACTION_LABELS;

  private readonly userId = this.route.snapshot.paramMap.get('id') ?? '';

  readonly user = signal<AdminUser | null>(null);
  readonly auditEntries = signal<AuditEntry[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly notFound = signal(false);
  readonly newInviteUrl = signal<string | null>(null);

  /**
   * A Manager must not be able to demote or offboard themselves — doing so could strip the
   * system of its last administrator. The backend refuses it too; this just avoids offering
   * an action that would only fail.
   */
  readonly isSelf = computed(() => this.user()?.id === this.auth.currentUser()?.userId);

  /** Every account but your own is administrable, Managers included. */
  readonly canEdit = computed(() => this.user() !== null && !this.isSelf());

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    // Only a Manager reaches this screen, which is what makes the address editable here and
    // nowhere else.
    address: ['', [Validators.maxLength(500)]],
    role: ['TECHNICIAN' as UserRole, [Validators.required]],
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.loading.set(true);

    this.adminUsers.get(this.userId).subscribe({
      next: (user) => {
        this.user.set(user);
        this.form.patchValue({
          name: user.name,
          phone: user.phone ?? '',
          address: user.address ?? '',
          role: user.role,
        });
        this.loading.set(false);
        this.loadAudit();
      },
      error: () => {
        this.loading.set(false);
        this.notFound.set(true);
      },
    });
  }

  private loadAudit(): void {
    this.adminUsers
      .audit({
        targetUserId: this.userId,
        action: null,
        q: '',
        from: null,
        to: null,
        page: 0,
        size: 20,
      })
      .subscribe({
        next: (page) => this.auditEntries.set(page.content),
        // Supporting detail; failing to load it must not blank the whole screen.
        error: () => this.auditEntries.set([]),
      });
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const { name, phone, address, role } = this.form.getRawValue();

    this.adminUsers
      .update(this.userId, {
        name,
        phone: phone.trim() || null,
        address: address.trim() || null,
        role,
      })
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.user.set(user);
          this.snackBar.open('Changes saved.', 'Dismiss', { duration: 4000 });
          this.loadAudit();
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.snackBar.open(this.messageFor(error, 'Could not save changes.'), 'Dismiss', {
            duration: 6000,
          });
        },
      });
  }

  deactivate(): void {
    const user = this.user();
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
      .open(ConfirmDialog, { data, width: '28rem' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.saving.set(true);
        this.adminUsers.deactivate(this.userId).subscribe({
          next: (updated) => this.afterStatusChange(updated, `${updated.name} deactivated.`),
          error: (error: unknown) => this.afterStatusError(error, 'Could not deactivate.'),
        });
      });
  }

  reactivate(): void {
    this.saving.set(true);
    this.adminUsers.reactivate(this.userId).subscribe({
      next: (updated) => this.afterStatusChange(updated, `${updated.name} reactivated.`),
      error: (error: unknown) => this.afterStatusError(error, 'Could not reactivate.'),
    });
  }

  resendInvite(): void {
    this.saving.set(true);
    this.adminUsers.resendInvite(this.userId).subscribe({
      next: (response) => {
        this.saving.set(false);
        this.newInviteUrl.set(response.activationUrl);
        this.snackBar.open('A new activation link has been issued.', 'Dismiss', { duration: 5000 });
        this.loadAudit();
      },
      error: (error: unknown) => {
        this.saving.set(false);
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

  private afterStatusChange(updated: AdminUser, message: string): void {
    this.saving.set(false);
    this.user.set(updated);
    this.newInviteUrl.set(null);
    this.snackBar.open(message, 'Dismiss', { duration: 4000 });
    this.loadAudit();
  }

  private afterStatusError(error: unknown, fallback: string): void {
    this.saving.set(false);
    this.snackBar.open(this.messageFor(error, fallback), 'Dismiss', { duration: 6000 });
  }

  /** Prefers the API's own explanation over a generic one. */
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
