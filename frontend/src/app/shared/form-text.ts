import { AbstractControl, ValidationErrors } from '@angular/forms';

/**
 * Returns a copy of `value` with every string, at any depth, trimmed. Run on a request payload
 * just before it is sent so stray leading/trailing whitespace from typing or pasting never
 * reaches the backend (a trailing space on an email otherwise fails the lookup outright).
 *
 * <p>Never pass a password through this — leading and trailing spaces are legitimate there.
 */
export function trimStrings<T>(value: T): T {
  if (typeof value === 'string') {
    return value.trim() as T;
  }
  if (Array.isArray(value)) {
    return value.map((item) => trimStrings(item)) as T;
  }
  if (value !== null && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value).map(([key, item]) => [key, trimStrings(item)]),
    ) as T;
  }
  return value;
}

/**
 * `Validators.required` treats "   " as filled in. This reports the same `required` error for
 * whitespace-only input, so existing `hasError('required')` messages keep working and the form
 * doesn't submit a value that trims to nothing.
 */
export function requiredTrimmed(control: AbstractControl): ValidationErrors | null {
  const value: unknown = control.value;
  return typeof value === 'string' && value.trim() === '' ? { required: true } : null;
}
