import { addressFromComponents } from './place-address';

const c = (longText: string, ...types: string[]) => ({ longText, types });
const country = (longText: string, shortText: string) => ({
  longText,
  shortText,
  types: ['country'],
});

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
        country('South Africa', 'ZA'),
      ]),
    ).toEqual({
      country: 'ZA',
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
      country: '',
      street: 'Route 62',
      suburb: '',
      city: 'Sarah Baartman District Municipality',
      province: '',
      postalCode: '',
    });
  });

  it('reads the country and keeps a region outside South Africa as written', () => {
    expect(
      addressFromComponents([
        c('12', 'street_number'),
        c('Moi Avenue', 'route'),
        c('Nairobi', 'locality'),
        c('Nairobi County', 'administrative_area_level_1'),
        country('Kenya', 'KE'),
      ]),
    ).toEqual({
      country: 'KE',
      street: '12 Moi Avenue',
      suburb: '',
      city: 'Nairobi',
      province: 'Nairobi County',
      postalCode: '',
    });
  });

  it('ignores a country the app does not serve', () => {
    expect(addressFromComponents([country('Antarctica', 'AQ')]).country).toBe('');
  });
});
