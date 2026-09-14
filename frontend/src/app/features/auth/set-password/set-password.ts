import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AccountService } from '../../../core/auth/account.service';
import {
  PASSWORD_MAX_LENGTH,
  PASSWORD_MIN_LENGTH,
  passwordValidators,
  passwordsMatch,
} from '../../../shared/password.validators';

export type SetPasswordMode = 'ACTIVATE' | 'RESET';

/**
 * Backs both `/activate` and `/reset-password`. The two screens differ only in their copy
 * and which endpoint they post to, so they share one implementation selected by route data.
 */
@Component({
  selector: 'app-set-password',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './set-password.html',
  styleUrl: '../auth-page.scss',
})
export class SetPassword {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly minLength = PASSWORD_MIN_LENGTH;
  readonly maxLength = PASSWORD_MAX_LENGTH;

  private readonly mode: SetPasswordMode = this.route.snapshot.data['mode'] ?? 'ACTIVATE';
  private readonly token = this.route.snapshot.queryParamMap.get('token') ?? '';

  readonly submitting = signal(false);
  readonly succeeded = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hidePassword = signal(true);

  /** A link with no token at all is broken before the user types anything. */
  readonly tokenMissing = computed(() => this.token.length === 0);

  readonly heading = this.mode === 'ACTIVATE' ? 'Activate your account' : 'Choose a new password';
  readonly subtitle =
    this.mode === 'ACTIVATE'
      ? 'Set a password to finish setting up your TechConnect account.'
      : 'Set a new password for your TechConnect account.';
  readonly submitLabel = this.mode === 'ACTIVATE' ? 'Activate account' : 'Reset password';

  readonly form = this.fb.nonNullable.group(
    {
      password: ['', passwordValidators],
      confirmPassword: ['', passwordValidators],
    },
    { validators: passwordsMatch() },
  );

  togglePassword(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  submit(): void {
    if (this.form.invalid || this.submitting() || this.tokenMissing()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { password } = this.form.getRawValue();
    const request =
      this.mode === 'ACTIVATE'
        ? this.account.activate(this.token, password)
        : this.account.resetPassword(this.token, password);

    request.subscribe({
      next: () => {
        this.submitting.set(false);
        this.succeeded.set(true);
        // Straight to login rather than auto-signing them in: setting a password does not
        // issue a token, and a reset deliberately revokes every existing session.
        setTimeout(() => void this.router.navigate(['/login']), 1800);
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(
          error instanceof HttpErrorResponse && error.status === 400
            ? 'This link is invalid or has expired. Links are single-use and last 72 hours. Request a new one below.'
            : 'Could not set your password right now. Please try again.',
        );
      },
    });
  }
}
