import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './login.html',
  styleUrl: '../auth-page.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hidePassword = signal(true);

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  togglePassword(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password).subscribe({
      next: (response) => {
        this.submitting.set(false);
        // Honour where the guard was taking them, falling back to their role's home.
        const redirectTo = this.route.snapshot.queryParamMap.get('redirectTo');
        void this.router.navigateByUrl(redirectTo ?? this.auth.homeRouteFor(response.role));
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        this.errorMessage.set(messageFor(error));
      },
    });
  }
}

/**
 * Shows whatever explanation the API gave.
 *
 * The backend keeps a 401 generic ("Invalid credentials") unless the caller supplied the
 * correct password, in which case it says why sign-in was refused — deactivated, or locked
 * after repeated failures. Rendering the server's message verbatim is what surfaces that,
 * and it keeps the two sides from drifting apart as the rules change.
 */
function messageFor(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    const apiMessage = (error.error as { error?: string } | null)?.error;
    if (error.status === 401 && apiMessage) {
      return apiMessage;
    }
  }
  return 'Could not sign in right now. Please try again.';
}
