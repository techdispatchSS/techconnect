import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AccountService, Profile } from '../../core/auth/account.service';

/**
 * Where the signed-in user maintains their own details — including a Manager, who is
 * deliberately absent from the user list they administer.
 *
 * <p>Address appears only for Managers. For everyone else the field is hidden rather than
 * shown disabled: a technician has no business seeing dispatch configuration presented as
 * something they own.
 */
@Component({
  selector: 'app-profile-dialog',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
  ],
  templateUrl: './profile-dialog.html',
})
export class ProfileDialog implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ProfileDialog, Profile | undefined>);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly profile = signal<Profile | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    address: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    this.account.me().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.form.patchValue({
          name: profile.name,
          phone: profile.phone ?? '',
          address: profile.address ?? '',
        });
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.errorMessage.set('Could not load your profile. Please try again.');
      },
    });
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const { name, phone, address } = this.form.getRawValue();

    this.account
      .updateProfile({
        name,
        phone: phone.trim() || null,
        address: address.trim() || null,
      })
      .subscribe({
        next: (updated) => {
          this.saving.set(false);
          this.snackBar.open('Profile updated.', 'Dismiss', { duration: 4000 });
          this.dialogRef.close(updated);
        },
        error: (error: unknown) => {
          this.saving.set(false);
          this.errorMessage.set(
            error instanceof HttpErrorResponse && error.status === 400
              ? 'Check the details and try again.'
              : 'Could not save your profile right now. Please try again.',
          );
        },
      });
  }
}
