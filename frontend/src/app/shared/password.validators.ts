import { AbstractControl, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';

/**
 * Must match the backend's `AuthDtos.PASSWORD_MIN`/`PASSWORD_MAX`. The upper bound is not
 * arbitrary: bcrypt ignores input past 72 bytes, so a longer password would have a silently
 * inert tail.
 */
export const PASSWORD_MIN_LENGTH = 12;
export const PASSWORD_MAX_LENGTH = 72;

export const passwordValidators: ValidatorFn[] = [
  Validators.required,
  Validators.minLength(PASSWORD_MIN_LENGTH),
  Validators.maxLength(PASSWORD_MAX_LENGTH),
];

/**
 * Cross-field check that the confirmation matches. Applied to the form group, with the error
 * placed on the confirmation control so it renders under the field the user must fix.
 */
export function passwordsMatch(
  passwordKey = 'password',
  confirmKey = 'confirmPassword',
): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get(passwordKey);
    const confirm = group.get(confirmKey);

    if (!password || !confirm || !confirm.value) {
      return null;
    }

    if (password.value === confirm.value) {
      // Clear only our own error so any other validator's state (minlength, required)
      // survives untouched.
      if (confirm.hasError('passwordMismatch')) {
        const remaining = { ...confirm.errors };
        delete remaining['passwordMismatch'];
        confirm.setErrors(Object.keys(remaining).length ? remaining : null);
      }
      return null;
    }

    confirm.setErrors({ ...confirm.errors, passwordMismatch: true });
    return { passwordMismatch: true };
  };
}
