/** Mirrors the backend `Province` enum — South Africa's nine provinces. */
export type Province =
  | 'EASTERN_CAPE'
  | 'FREE_STATE'
  | 'GAUTENG'
  | 'KWAZULU_NATAL'
  | 'LIMPOPO'
  | 'MPUMALANGA'
  | 'NORTHERN_CAPE'
  | 'NORTH_WEST'
  | 'WESTERN_CAPE';

export const PROVINCE_LABELS: Record<Province, string> = {
  EASTERN_CAPE: 'Eastern Cape',
  FREE_STATE: 'Free State',
  GAUTENG: 'Gauteng',
  KWAZULU_NATAL: 'KwaZulu-Natal',
  LIMPOPO: 'Limpopo',
  MPUMALANGA: 'Mpumalanga',
  NORTHERN_CAPE: 'Northern Cape',
  NORTH_WEST: 'North West',
  WESTERN_CAPE: 'Western Cape',
};

/** Ordered for a `<select>` — alphabetical by label, not enum declaration order. */
export const PROVINCES: readonly Province[] = (Object.keys(PROVINCE_LABELS) as Province[]).sort(
  (a, b) => PROVINCE_LABELS[a].localeCompare(PROVINCE_LABELS[b]),
);

/**
 * A structured postal address. Every field is required wherever this appears in a request —
 * street, suburb, city, province and postal code are captured individually so distance-based
 * job matching (PRD Phase 2) never has to work around a gap the way a person reading a
 * free-text address could.
 */
export interface Address {
  street: string;
  suburb: string;
  city: string;
  province: Province;
  postalCode: string;
}

/** Same shape as {@link Address}; a distinct alias documents that this is what a form submits,
 * not what the server already has on file. */
export type AddressRequest = Address;
