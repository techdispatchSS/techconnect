import { Address, PROVINCE_LABELS, PROVINCES, Province } from '../address.models';

/** The subset of a Google Places `AddressComponent` this mapping reads. */
export interface PlaceAddressComponent {
  readonly longText: string | null;
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

function provinceFrom(name: string): Province | '' {
  const wanted = normalise(name);
  return PROVINCES.find((p) => normalise(PROVINCE_LABELS[p]) === wanted) ?? '';
}

/**
 * Maps Google's address components onto this app's structured South African address.
 * Anything Google doesn't supply comes back as an empty string, so the caller can fill what it
 * got and leave the rest for the person to complete.
 */
export function addressFromComponents(
  components: readonly PlaceAddressComponent[],
): Omit<Address, 'province'> & { province: Province | '' } {
  const streetNumber = componentText(components, 'street_number');
  const route = componentText(components, 'route');

  return {
    street: [streetNumber, route].filter(Boolean).join(' '),
    suburb: componentText(components, 'sublocality_level_1', 'sublocality', 'neighborhood'),
    city: componentText(components, 'locality', 'administrative_area_level_2'),
    province: provinceFrom(componentText(components, 'administrative_area_level_1')),
    postalCode: componentText(components, 'postal_code'),
  };
}
