import { addressFromComponents } from './place-address';

const c = (longText: string, ...types: string[]) => ({ longText, types });

describe('addressFromComponents', () => {
  it('maps a full South African address', () => {
    expect(
      addressFromComponents([
        c('12', 'street_number'),
        c('Long Street', 'route'),
        c('Gardens', 'sublocality_level_1', 'sublocality'),
        c('Cape Town', 'locality'),
        c('Western Cape', 'administrative_area_level_1'),
        c('8001', 'postal_code'),
        c('South Africa', 'country'),
      ]),
    ).toEqual({
      street: '12 Long Street',
      suburb: 'Gardens',
      city: 'Cape Town',
      province: 'WESTERN_CAPE',
      postalCode: '8001',
    });
  });

  it('matches KwaZulu-Natal despite punctuation and casing', () => {
    expect(
      addressFromComponents([c('Kwazulu Natal', 'administrative_area_level_1')]).province,
    ).toBe('KWAZULU_NATAL');
  });

  it('falls back to the municipality when there is no locality, and leaves gaps blank', () => {
    expect(
      addressFromComponents([
        c('Route 62', 'route'),
        c('Sarah Baartman District Municipality', 'administrative_area_level_2'),
        c('Somewhere Else', 'administrative_area_level_1'),
      ]),
    ).toEqual({
      street: 'Route 62',
      suburb: '',
      city: 'Sarah Baartman District Municipality',
      province: '',
      postalCode: '',
    });
  });
});
