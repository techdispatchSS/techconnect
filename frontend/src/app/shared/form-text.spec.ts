import { FormControl } from '@angular/forms';

import { requiredTrimmed, trimStrings } from './form-text';

describe('trimStrings', () => {
  it('trims nested strings and leaves other values alone', () => {
    const input = {
      firstName: '  Nomsa ',
      phone: null,
      age: 3,
      address: { street: ' 12 Long Street\t', postalCode: '8001 ' },
      tags: [' a ', 'b'],
    };
    expect(trimStrings(input)).toEqual({
      firstName: 'Nomsa',
      phone: null,
      age: 3,
      address: { street: '12 Long Street', postalCode: '8001' },
      tags: ['a', 'b'],
    });
  });

  it('does not mutate its input', () => {
    const input = { name: ' x ' };
    trimStrings(input);
    expect(input.name).toBe(' x ');
  });
});

describe('requiredTrimmed', () => {
  it('rejects empty and whitespace-only values', () => {
    expect(requiredTrimmed(new FormControl(''))).toEqual({ required: true });
    expect(requiredTrimmed(new FormControl('   '))).toEqual({ required: true });
    expect(requiredTrimmed(new FormControl(' a '))).toBeNull();
  });
});
