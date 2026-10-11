import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';
import { ThemeService } from '../../../core/theme/theme.service';
import { TrimOnBlur } from '../../../shared/trim-on-blur';

interface HeroStat {
  readonly value: string;
  readonly label: string;
}

// No support inbox is configured anywhere in the backend (techconnect.mail.from is a
// no-reply *sending* address only) — this is a placeholder. Swap it for wherever sign-in
// trouble should actually land before this ships.
const SUPPORT_EMAIL = 'support@tech-connect.app';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, TrimOnBlur],
  templateUrl: './login.html',
  styleUrl: './login.scss',
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly themeService = inject(ThemeService);

  readonly theme = this.themeService.theme;

  toggleTheme(): void {
    this.themeService.toggle();
  }

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly hidePassword = signal(true);

  // Placeholder brand copy, not wired to a real count — swap for real figures once there's
  // somewhere to source them from an unauthenticated screen.
  readonly heroStats: readonly HeroStat[] = [
    { value: '24/7', label: 'Field coverage' },
    { value: '8h', label: 'Session length' },
    { value: '3', label: 'Roles, one board' },
  ];

  readonly form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
    remember: [true],
  });

  togglePassword(): void {
    this.hidePassword.update((hidden) => !hidden);
  }

  /** Pre-fills the message with what the recipient needs — including the email they were
   * trying to sign in with, if they'd already typed one — so a struggling technician doesn't
   * have to compose a support request from a blank subject line. */
  contactHref(): string {
    const typedEmail = this.form.controls.email.value.trim();
    const subject = encodeURIComponent('Trouble signing in to TechConnect');
    const body = encodeURIComponent(
      `Hi,\n\nI'm having trouble signing in to TechConnect.\n\n` +
        `My email: ${typedEmail || '(enter the email you sign in with)'}\n` +
        `What's happening: \n`,
    );
    return `mailto:${SUPPORT_EMAIL}?subject=${subject}&body=${body}`;
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { email, password, remember } = this.form.getRawValue();

    this.auth.login(email, password, remember).subscribe({
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
