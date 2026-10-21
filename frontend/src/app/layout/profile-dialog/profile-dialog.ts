import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  OnInit,
  ViewEncapsulation,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';

import { bindAddressRules } from '../../core/address-rules';
import {
  COUNTRIES,
  DEFAULT_COUNTRY,
  PROVINCES,
  PROVINCE_LABELS,
  isSouthAfrica,
} from '../../core/address.models';
import { AccountService, Profile } from '../../core/auth/account.service';
import { trimStrings } from '../../shared/form-text';
import { Skeleton } from '../../shared/skeleton/skeleton';
import { PickedAddress, StreetInput } from '../../shared/street-input/street-input';
import { TrimOnBlur } from '../../shared/trim-on-blur';

/**
 * Unlike the admin portal's onboarding and edit forms, a Manager's own address is optional
 * here: leaving every field blank is fine (they may never have set one), but a partial
 * address is not. The country is always set, so it is not what "blank" is judged by.
 *
 * <p>Street, city and region must all be filled in. Suburb and a four-digit postcode are also
 * required in South Africa; elsewhere they are optional (the postcode's shape is checked by
 * the field itself).
 */
function completeOrEmptyAddress(group: AbstractControl): ValidationErrors | null {
  const value = group.value as Record<string, string>;
  const text = (key: string): string => (value[key] ?? '').trim();

  const always = ['street', 'city', 'province'];
  const sa = isSouthAfrica(value['country']);
  const required = sa ? [...always, 'suburb', 'postalCode'] : always;
  const filled = ['street', 'suburb', 'city', 'province', 'postalCode'].some((k) => text(k) !== '');

  if (!filled) {
    return null;
  }
  return required.every((k) => text(k) !== '') ? null : { incomplete: true };
}

@Component({
  selector: 'app-profile-dialog',
  imports: [ReactiveFormsModule, MatDialogModule, Skeleton, TrimOnBlur, StreetInput],
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

  private readonly destroyRef = inject(DestroyRef);
  readonly countries = COUNTRIES;
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
        country: [DEFAULT_COUNTRY],
        street: ['', [Validators.maxLength(255)]],
        suburb: ['', [Validators.maxLength(120)]],
        city: ['', [Validators.maxLength(120)]],
        province: [''],
        postalCode: [''],
      },
      { validators: completeOrEmptyAddress },
    ),
  });

  readonly country = toSignal(this.form.controls.address.controls.country.valueChanges, {
    initialValue: this.form.controls.address.controls.country.value,
  });
  readonly inSouthAfrica = computed(() => isSouthAfrica(this.country()));

  ngOnInit(): void {
    bindAddressRules(this.form.controls.address, this.destroyRef, { required: false });
    this.account.me().subscribe({
      next: (profile) => {
        this.profile.set(profile);
        this.form.patchValue({
          name: profile.name,
          phone: profile.phone ?? '',
          address: {
            country: profile.address?.country ?? DEFAULT_COUNTRY,
            street: profile.address?.street ?? '',
            suburb: profile.address?.suburb ?? '',
            city: profile.address?.city ?? '',
            province: profile.address?.province ?? '',
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

  /** Fills the rest of the address from a Google suggestion; anything it didn't return is left
   * as it was so it can be completed by hand. */
  applyPlace(place: PickedAddress): void {
    const group = this.form.controls.address;
    for (const [key, value] of Object.entries(place)) {
      if (value) {
        group.get(key)?.setValue(value);
        group.get(key)?.markAsTouched();
      }
    }
    this.form.markAsDirty();
  }

  save(): void {
    if (this.form.invalid || this.saving()) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.errorMessage.set(null);

    const { name, phone, address } = this.form.getRawValue();
    const { country, ...parts } = address;
    const addressProvided = Object.values(parts).some((v) => v.trim() !== '');

    this.account
      .updateProfile(
        trimStrings({
          name,
          phone: phone.trim() || null,
          address: addressProvided ? { ...address, country } : undefined,
        }),
      )
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
