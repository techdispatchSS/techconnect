import { DestroyRef } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormGroup, ValidatorFn, Validators } from '@angular/forms';

import { requiredTrimmed } from '../shared/form-text';

const SA_POSTCODE = /^\d{4}$/;
/** Letters, digits, space and hyphen cover every postcode format in use. */
const POSTCODE = /^[A-Za-z0-9][A-Za-z0-9 -]{0,11}$/;

/** A stray leading space (Enter submits before the blur trim runs) must not fail the pattern. */
function onTrimmed(validator: ValidatorFn): ValidatorFn {
  return (control: AbstractControl) => {
    const value: unknown = control.value;
    return validator({
      value: typeof value === 'string' ? value.trim() : value,
    } as AbstractControl);
  };
}

/** Only a country that has been chosen, and is South Africa, gets South Africa's rules. */
const isSouthAfrica = (code: string | null | undefined): boolean => code === 'ZA';

function set(control: AbstractControl | null, validators: ValidatorFn[]): void {
  control?.setValidators(validators);
  control?.updateValueAndValidity({ emitEvent: false });
}

/**
 * Makes an address form follow its `country` control. South Africa keeps what it always had: a
 * suburb, one of the nine provinces and a four-digit postcode, all required. Anywhere else the
 * region is typed, and suburb and postcode are optional (many countries have neither), though a
 * postcode that is given must still look like one.
 *
 * <p>`required: false` is for a form where the whole address is optional (a manager's own
 * profile); the shape rules still apply to whatever is filled in.
 *
 * <p>Changing country clears the region when it moves between South Africa's list and free
 * text, since "Gauteng" means nothing in Kenya and "Nairobi County" is not a South African
 * province. The country control must come first in the group, so a form that sets every field
 * at once (a Google suggestion, a reset) sets it before the region.
 */
export function bindAddressRules(
  group: FormGroup,
  destroyRef: DestroyRef,
  options: { required: boolean } = { required: true },
): void {
  const country = group.get('country');
  if (!country) {
    return;
  }
  let current = country.value as string;

  const apply = (code: string): void => {
    const sa = isSouthAfrica(code);
    const required = options.required;
    set(
      group.get('suburb'),
      sa && required ? [requiredTrimmed, Validators.maxLength(120)] : [Validators.maxLength(120)],
    );
    set(
      group.get('province'),
      required ? [Validators.required, Validators.maxLength(120)] : [Validators.maxLength(120)],
    );
    set(group.get('postalCode'), [
      ...(sa && required ? [Validators.required] : []),
      onTrimmed(Validators.pattern(sa ? SA_POSTCODE : POSTCODE)),
    ]);
  };

  apply(current);
  country.valueChanges.pipe(takeUntilDestroyed(destroyRef)).subscribe((code: string) => {
    if (code === current) {
      return;
    }
    if (isSouthAfrica(current) !== isSouthAfrica(code)) {
      group.get('province')?.setValue('');
    }
    current = code;
    apply(code);
  });
}
