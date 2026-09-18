import { Directive, HostListener, inject } from '@angular/core';
import { NgControl } from '@angular/forms';

/**
 * Trims a text field's value when it loses focus, so a pasted "  name@example.com " is fixed
 * where the person can see it — and validators like `email` and the postal-code pattern judge
 * the trimmed value rather than rejecting the stray space.
 *
 * <p>Opt-in per input on purpose. Never put it on a password field: leading and trailing
 * spaces are legitimate there.
 */
@Directive({ selector: 'input[appTrimOnBlur]' })
export class TrimOnBlur {
  private readonly control = inject(NgControl);

  @HostListener('blur')
  trim(): void {
    const value: unknown = this.control.value;
    if (typeof value === 'string' && value !== value.trim()) {
      this.control.control?.setValue(value.trim());
    }
  }
}
