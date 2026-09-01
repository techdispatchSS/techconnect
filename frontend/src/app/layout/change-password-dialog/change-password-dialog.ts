import { HttpErrorResponse } from '@angular/common/http';
import { Component, ViewEncapsulation, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AccountService } from '../../core/auth/account.service';
import { AuthService } from '../../core/auth/auth.service';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  passwordValidators,
  passwordsMatch,
} from '../../shared/password.validators';

/** Self-service password change, available to every role from the toolbar menu. */
@Component({
  selector: 'app-change-password-dialog',
  imports: [ReactiveFormsModule, MatDialogModule],
  templateUrl: './change-password-dialog.html',
  styleUrl: './change-password-dialog.scss',
  // See profile-dialog.ts for why: this dialog's content portals to <body>, outside the
  // component tree, so scoped encapsulation (and any ancestor's CSS custom properties)
  // wouldn't reach it either way.
  encapsulation: ViewEncapsulation.None,
})
export class ChangePasswordDialog {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);
  private readonly auth = inject(AuthService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ChangePasswordDialog>);

  readonly minLength = PASSWORD_MIN_LENGTH;
  readonly maxLength = PASSWORD_MAX_LENGTH;

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly form = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      password: ['', passwordValidators],
      confirmPassword: ['', passwordValidators],
    },
    { validators: passwordsMatch() },
  );

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { currentPassword, password } = this.form.getRawValue();

    this.account.changePassword(currentPassword, password).subscribe({
      next: () => {
        this.submitting.set(false);
        this.dialogRef.close(true);
        // The backend revokes every existing session on a password change, so the token in
        // this tab is already dead. Sign out cleanly rather than let the next request 401.
        this.snackBar.open('Password changed. Please sign in again.', 'Dismiss', {
          duration: 6000,
        });
        this.auth.logout();
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error instanceof HttpErrorResponse && error.status === 401
            ? 'Your current password is incorrect.'
            : 'Could not change your password right now. Please try again.',
        );
      },
    });
  }
}
