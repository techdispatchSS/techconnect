import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Router, RouterLink } from '@angular/router';

import { PROVINCES, PROVINCE_LABELS, Province } from '../../../core/address.models';
import { UserRole } from '../../../core/auth/auth.models';
import { AdminNavCountsService } from '../admin-nav-counts.service';
import { AdminUserService } from '../admin-user.service';
import { CreateUserResponse, ROLE_BLURBS, ROLE_LABELS } from '../admin.models';
import { AdminIcon } from '../ui/admin-icon/admin-icon';

interface RoleCard {
  readonly role: UserRole;
  readonly label: string;
  readonly blurb: string;
}

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
  imports: [ReactiveFormsModule, RouterLink, MatSnackBarModule, AdminIcon],
  templateUrl: './user-create.html',
  styleUrl: './user-create.scss',
})
export class UserCreate {
  private readonly fb = inject(FormBuilder);
  private readonly adminUsers = inject(AdminUserService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly counts = inject(AdminNavCountsService);

  readonly roleCards = ROLE_CARDS;
  readonly roleLabels = ROLE_LABELS;
  readonly provinces = PROVINCES;
  readonly provinceLabels = PROVINCE_LABELS;

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly created = signal<CreateUserResponse | null>(null);

  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    phone: ['', [Validators.maxLength(32)]],
    address: this.fb.nonNullable.group({
      street: ['', [Validators.required, Validators.maxLength(255)]],
      suburb: ['', [Validators.required, Validators.maxLength(120)]],
      city: ['', [Validators.required, Validators.maxLength(120)]],
      province: ['' as Province | '', [Validators.required]],
      postalCode: ['', [Validators.required, Validators.pattern(/^\d{4}$/)]],
    }),
    role: ['TECHNICIAN' as UserRole, [Validators.required]],
  });

  // Zoneless change detection: a template read of `form.controls.name.value` never
  // re-renders on keystrokes, since it isn't a signal — the invite preview needs these.
  readonly previewName = toSignal(this.form.controls.name.valueChanges, {
    initialValue: this.form.controls.name.value,
  });
  readonly previewRole = toSignal(this.form.controls.role.valueChanges, {
    initialValue: this.form.controls.role.value,
  });

  pickRole(role: UserRole): void {
    this.form.controls.role.setValue(role);
  }

  submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const { name, email, phone, address, role } = this.form.getRawValue();

    this.adminUsers
      .create({
        name,
        email,
        phone: phone.trim() || null,
        address: { ...address, province: address.province as Province },
        role,
      })
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.created.set(response);
          this.counts.refresh();
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          if (error instanceof HttpErrorResponse && error.status === 409) {
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
    this.form.reset({
      name: '',
      email: '',
      phone: '',
      address: { street: '', suburb: '', city: '', province: '', postalCode: '' },
      role: 'TECHNICIAN',
    });
  }
}
