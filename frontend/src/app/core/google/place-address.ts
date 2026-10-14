import { Address, COUNTRIES, PROVINCE_LABELS, PROVINCES } from '../address.models';

/** The subset of a Google Places `AddressComponent` this mapping reads. */
export interface PlaceAddressComponent {
  readonly longText: string | null;
  readonly shortText?: string | null;
  readonly types: readonly string[];
}

/** Letters only, lower-cased, so "KwaZulu-Natal" and "Kwazulu Natal" both match. */
function normalise(value: string): string {
  return value.toLowerCase().replace(/[^a-z]/g, '');
}

function componentText(components: readonly PlaceAddressComponent[], ...types: string[]): string {
  for (const type of types) {
    const match = components.find((c) => c.types.includes(type));
    if (match?.longText) {
      return match.longText;
    }
  }
  return '';
}

/** A South African province's name from Google's text; anything else is kept as written. */
function regionFrom(name: string, southAfrica: boolean): string {
  if (!southAfrica) {
    return name;
  }
  const wanted = normalise(name);
  return PROVINCES.find((p) => normalise(PROVINCE_LABELS[p]) === wanted) ?? '';
}

/** The country's code if Google gave one and it is a country the app serves, else ''. */
function countryFrom(components: readonly PlaceAddressComponent[]): string {
  const code = components.find((c) => c.types.includes('country'))?.shortText?.toUpperCase() ?? '';
  return COUNTRIES.some((c) => c.code === code) ? code : '';
}

/**
 * Maps Google's address components onto this app's structured address. Anything Google doesn't
 * supply comes back as an empty string, so the caller can fill what it got and leave the rest
 * for the person to complete.
 *
 * <p>`country` is the first key on purpose: a form that applies the fields in order sets the
 * country before the region, and changing country clears the region.
 */
export function addressFromComponents(components: readonly PlaceAddressComponent[]): Address {
  const streetNumber = componentText(components, 'street_number');
  const route = componentText(components, 'route');
  const country = countryFrom(components);

  return {
    country,
    street: [streetNumber, route].filter(Boolean).join(' '),
    suburb: componentText(components, 'sublocality_level_1', 'sublocality', 'neighborhood'),
    city: componentText(components, 'locality', 'administrative_area_level_2'),
    province: regionFrom(
      componentText(components, 'administrative_area_level_1'),
      !country || country === 'ZA',
    ),
    postalCode: componentText(components, 'postal_code'),
  };
}
