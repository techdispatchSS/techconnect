import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AccountService } from '../../../core/auth/account.service';
import { TrimOnBlur } from '../../../shared/trim-on-blur';

@Component({
  selector: 'app-forgot-password',
  imports: [ReactiveFormsModule, RouterLink, TrimOnBlur],
  templateUrl: './forgot-password.html',
  styleUrl: '../auth-page.scss',
})
export class ForgotPassword {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);

  readonly submitting = signal(false);
  readonly submitted = signal(false);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
  });

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);

    this.account.forgotPassword(this.form.getRawValue().email).subscribe({
      // Both outcomes land in the same place on purpose. The confirmation must not reveal
      // whether the address belongs to an account, or this screen becomes a way to discover
      // who works here.
      next: () => {
        this.submitting.set(false);
        this.submitted.set(true);
      },
      error: () => {
        this.submitting.set(false);
        this.submitted.set(true);
      },
    });
  }
}
