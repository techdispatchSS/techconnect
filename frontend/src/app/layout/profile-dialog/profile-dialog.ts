import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

import { PROVINCES, PROVINCE_LABELS, Province } from '../../core/address.models';
import { AccountService, Profile } from '../../core/auth/account.service';

/**
 * Unlike the admin portal's onboarding and edit forms, a Manager's own address is optional
 * here: leaving every field blank is fine (they may never have set one), but a partial
 * address is not — either all five fields are present and valid, or none are.
 */
function completeOrEmptyAddress(group: AbstractControl): ValidationErrors | null {
  const controls = group.value as Record<string, string>;
  const values = Object.values(controls).map((v) => (v ?? '').trim());

  if (values.every((v) => v === '')) {
    return null;
  }
  if (values.some((v) => v === '')) {
    return { incomplete: true };
  }
  return /^\d{4}$/.test(controls['postalCode']) ? null : { incomplete: true };
}

@Component({
  selector: 'app-profile-dialog',
  imports: [ReactiveFormsModule, MatDialogModule],
  templateUrl: './profile-dialog.html',
  styleUrl: './profile-dialog.scss',
  // Matches admin-shell.ts / login's approach: the "Industry" design system's tokens are
  // defined once, scoped by the `pd-` class prefix rather than Angular's per-component
  // encapsulation, because this dialog's content renders inside a CDK overlay portaled to
  // <body> — a sibling of the component tree, not a descendant — so scoped encapsulation
  // wouldn't reach it and CSS custom properties from an ancestor wouldn't cascade in either.
  encapsulation: ViewEncapsulation.None,
})
export class ProfileDialog implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly account = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly dialogRef = inject(MatDialogRef<ProfileDialog, Profile | undefined>);

  readonly provinces = PROVINCES;
  readonly provinceLabels = PROVINCE_LABELS;

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly profile = signal<Profile | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    address: this.fb.nonNullable.group(
      {
        street: ['', [Validators.maxLength(255)]],
        suburb: ['', [Validators.maxLength(120)]],
        city: ['', [Validators.maxLength(120)]],
        province: ['' as Province | ''],
        postalCode: ['', [Validators.maxLength(4)]],
      },
      { validators: completeOrEmptyAddress },
    ),
  });

  ngOnInit(): void {
    this.account.me().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.form.patchValue({
          name: profile.name,
          phone: profile.phone ?? '',
          address: {
            street: profile.address?.street ?? '',
            suburb: profile.address?.suburb ?? '',
            city: profile.address?.city ?? '',
            province: (profile.address?.province ?? '') as Province | '',
            postalCode: profile.address?.postalCode ?? '',
          },
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
    const addressProvided = Object.values(address).some((v) => v.trim() !== '');

    this.account
      .updateProfile({
        name,
        phone: phone.trim() || null,
        address: addressProvided ? { ...address, province: address.province as Province } : undefined,
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
