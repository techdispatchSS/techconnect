import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';

import { PROVINCES, PROVINCE_LABELS, Province } from '../../../core/address.models';
import { UserRole } from '../../../core/auth/auth.models';
import { requiredTrimmed, trimStrings } from '../../../shared/form-text';
import { PickedAddress, StreetInput } from '../../../shared/street-input/street-input';
import { TrimOnBlur } from '../../../shared/trim-on-blur';
import { AdminNavCountsService } from '../admin-nav-counts.service';
import { AdminUserService } from '../admin-user.service';
import { CreateUserResponse, ROLE_BLURBS, ROLE_LABELS } from '../admin.models';
import { AdminIcon } from '../ui/admin-icon/admin-icon';

interface RoleCard {
  readonly role: UserRole;
  readonly label: string;
  readonly blurb: string;
}

interface WizardStep {
  readonly n: 1 | 2 | 3;
  readonly label: string;
  readonly hint: string;
}

const STEPS: readonly WizardStep[] = [
  { n: 1, label: 'Details', hint: 'Name and contact' },
  { n: 2, label: 'Role & access', hint: 'Permissions and region' },
  { n: 3, label: 'Review & send', hint: 'Check, then send the invite' },
];

const ROLE_CARDS: readonly RoleCard[] = (['TECHNICIAN', 'CONTROLLER', 'MANAGER'] as const).map(
  (role) => ({ role, label: ROLE_LABELS[role], blurb: ROLE_BLURBS[role] }),
);

/**
 * Onboards a Controller, Technician or Manager. A full page rather than a dialog, per the
 * redesign — "Add user" is its own destination in the sidebar, not a modal spawned from the
 * directory.
 *
 * <p>No password field appears anywhere: the backend mints a single-use activation link and
 * the user chooses their own password.
 */
@Component({
  selector: 'app-user-create',
  imports: [ReactiveFormsModule, RouterLink, MatSnackBarModule, AdminIcon, StreetInput, TrimOnBlur],
  templateUrl: './user-create.html',
  styleUrl: './user-create.scss',
})
export class UserCreate {
  private readonly fb = inject(FormBuilder);
  private readonly adminUsers = inject(AdminUserService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly counts = inject(AdminNavCountsService);

  readonly steps = STEPS;
  readonly roleCards = ROLE_CARDS;
  readonly roleLabels = ROLE_LABELS;
  readonly provinces = PROVINCES;
  readonly provinceLabels = PROVINCE_LABELS;

  /** Which of the three steps is showing. Each is validated before the next opens. */
  readonly step = signal<1 | 2 | 3>(1);
  readonly submitting = signal(false);
  /** The server said this email already belongs to someone; cleared as soon as it is edited. */
  readonly emailTaken = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly created = signal<CreateUserResponse | null>(null);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [requiredTrimmed, Validators.maxLength(255)]],
    lastName: ['', [requiredTrimmed, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    address: this.fb.nonNullable.group({
      street: ['', [requiredTrimmed, Validators.maxLength(255)]],
      suburb: ['', [requiredTrimmed, Validators.maxLength(120)]],
      city: ['', [requiredTrimmed, Validators.maxLength(120)]],
      province: ['' as Province | '', [Validators.required]],
      postalCode: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    }),
    role: ['TECHNICIAN' as UserRole, [Validators.required]],
  });

  // Zoneless change detection: a template read of `form.controls.firstName.value` never
  // re-renders on keystrokes, since it isn't a signal — the invite preview needs these.
  readonly previewFirstName = toSignal(this.form.controls.firstName.valueChanges, {
    initialValue: this.form.controls.firstName.value,
  });
  readonly previewRole = toSignal(this.form.controls.role.valueChanges, {
    initialValue: this.form.controls.role.value,
  });

  constructor() {
    // Editing the email is how the person answers "that one is taken".
    this.form.controls.email.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.emailTaken.set(false));
  }

  /** Fills the rest of the address from a Google suggestion. Anything Google didn't return is
   * left as it was, so a person can complete it by hand. */
  applyPlace(place: PickedAddress): void {
    const group = this.form.controls.address;
    for (const [key, value] of Object.entries(place)) {
      if (value) {
        group.get(key)?.setValue(value);
        group.get(key)?.markAsTouched();
      }
    }
  }

  /** The fields each step owns, so "Next" only judges what is in front of the person. */
  private stepGroup(n: 1 | 2): AbstractControl[] {
    const c = this.form.controls;
    return n === 1 ? [c.firstName, c.lastName, c.email, c.phone] : [c.address, c.role];
  }

  private stepValid(n: 1 | 2): boolean {
    return this.stepGroup(n).every((control) => control.valid);
  }

  /** Marks the step's fields touched so their errors show, and reports whether it can advance. */
  private validateStep(n: 1 | 2): boolean {
    for (const control of this.stepGroup(n)) {
      control.markAllAsTouched();
    }
    return this.stepValid(n) && !(n === 1 && this.emailTaken());
  }

  next(): void {
    const current = this.step();
    if (current === 3 || !this.validateStep(current)) {
      return;
    }
    this.step.set((current + 1) as 2 | 3);
  }

  back(): void {
    const current = this.step();
    if (current > 1) {
      this.step.set((current - 1) as 1 | 2);
    }
  }

  /** Jump back to a step already completed (the progress strip and the review's Edit links). */
  goTo(n: 1 | 2 | 3): void {
    if (n < this.step()) {
      this.step.set(n);
    }
  }

  /** Enter in a field advances the wizard; only the last step sends anything. */
  onSubmit(): void {
    if (this.step() === 3) {
      this.submit();
    } else {
      this.next();
    }
  }

  /** What the review step shows, in the words the person would use. */
  summary(): {
    name: string;
    email: string;
    phone: string;
    roleLabel: string;
    roleBlurb: string;
    address: string;
  } {
    const v = this.form.getRawValue();
    const a = v.address;
    const province = a.province ? this.provinceLabels[a.province] : '';
    return {
      name: `${v.firstName} ${v.lastName}`.trim(),
      email: v.email.trim(),
      phone: v.phone.trim(),
      roleLabel: ROLE_LABELS[v.role],
      roleBlurb: ROLE_BLURBS[v.role],
      address: [a.street, a.suburb, a.city, province, a.postalCode]
        .map((part) => part.trim())
        .filter(Boolean)
        .join(', '),
    };
  }

  pickRole(role: UserRole): void {
    this.form.controls.role.setValue(role);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      // The problem is on an earlier step; take the person back to it.
      if (!this.stepValid(1)) {
        this.step.set(1);
      } else if (!this.stepValid(2)) {
        this.step.set(2);
      }
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { firstName, lastName, email, phone, address, role } = this.form.getRawValue();

    this.adminUsers
      .create(
        trimStrings({
          firstName,
          lastName,
          email,
          phone: phone.trim() || null,
          address: { ...address, province: address.province as Province },
          role,
        }),
      )
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.created.set(response);
          this.counts.refresh();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (error instanceof HttpErrorResponse && error.status === 409) {
            // A signal rather than a control error: the email field is re-created when the
            // person is taken back to step 1, which would wipe an error set on the control.
            this.emailTaken.set(true);
            this.form.controls.email.markAsTouched();
            // The email lives on step 1, so that is where the message has to be seen.
            this.step.set(1);
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
      this.snackBar.open('Copy blocked by the browser. Select the link manually.', 'Dismiss', {
        duration: 6000,
      });
    }
  }

  goToPerson(): void {
    const id = this.created()?.user.id;
    void this.router.navigate(['/admin/users'], id ? { queryParams: { selected: id } } : {});
  }

  addAnother(): void {
    this.created.set(null);
    this.step.set(1);
    this.form.reset({
      firstName: '',
      lastName: '',
      email: '',
      phone: '',
      address: { street: '', suburb: '', city: '', province: '', postalCode: '' },
      role: 'TECHNICIAN',
    });
  }
}
