import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';

import { UserRole } from '../../../core/auth/auth.models';
import { AdminUserService } from '../admin-user.service';
import { CreateUserResponse } from '../admin.models';

/**
 * Onboards a Controller or Technician.
 *
 * <p>No password field appears anywhere: the backend mints a single-use activation link and
 * the user chooses their own password. On success this dialog shows that link with a copy
 * button, so a Manager can onboard someone even when email delivery is not yet configured
 * or the recipient's inbox is unreachable.
 */
@Component({
  selector: 'app-user-create-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
  ],
  templateUrl: './user-create-dialog.html',
  styleUrl: './user-create-dialog.scss',
})
export class UserCreateDialog {
  private readonly fb = inject(FormBuilder);
  private readonly adminUsers = inject(AdminUserService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<UserCreateDialog, boolean>);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly created = signal<CreateUserResponse | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    // Required: a technician's address is the origin point for distance-based job matching,
    // so onboarding one without it would leave them undispatchable.
    address: ['', [Validators.required, Validators.maxLength(500)]],
    role: ['TECHNICIAN' as UserRole, [Validators.required]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { name, email, phone, address, role } = this.form.getRawValue();

    this.adminUsers
      .create({ name, email, phone: phone.trim() || null, address: address.trim(), role })
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.created.set(response);
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (error instanceof HttpErrorResponse && error.status === 409) {
            // Attach it to the field that caused it rather than showing a detached banner.
            this.form.controls.email.setErrors({ duplicate: true });
            this.form.controls.email.markAsTouched();
            return;
          }
          this.errorMessage.set(
            error instanceof HttpErrorResponse && error.status === 400
              ? ((error.error as { error?: string })?.error ?? 'Check the details and try again.')
              : 'Could not create this user right now. Please try again.',
          );
        },
      });
  }

  async copyActivationUrl(): Promise<void> {
    const url = this.created()?.activationUrl;
    if (!url) {
      return;
    }

    try {
      await navigator.clipboard.writeText(url);
      this.snackBar.open('Activation link copied.', 'Dismiss', { duration: 4000 });
    } catch {
      // Clipboard access can be blocked; the link is on screen and selectable regardless.
      this.snackBar.open('Copy blocked by the browser. Select the link manually.', 'Dismiss', {
        duration: 6000,
      });
    }
  }

  /** Signals to the list that it should refetch. */
  done(): void {
    this.dialogRef.close(true);
  }
}
