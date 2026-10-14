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

/** The countries the app is used in, keyed by ISO 3166-1 alpha-2 code. Mirrors the backend. */
export interface CountryOption {
  readonly code: string;
  readonly name: string;
  /** International dialling code without the plus. */
  readonly dialCode: string;
}

export const COUNTRIES: readonly CountryOption[] = [
  { code: 'DZ', name: 'Algeria', dialCode: '213' },
  { code: 'AO', name: 'Angola', dialCode: '244' },
  { code: 'BJ', name: 'Benin', dialCode: '229' },
  { code: 'BW', name: 'Botswana', dialCode: '267' },
  { code: 'BF', name: 'Burkina Faso', dialCode: '226' },
  { code: 'BI', name: 'Burundi', dialCode: '257' },
  { code: 'CV', name: 'Cabo Verde', dialCode: '238' },
  { code: 'CM', name: 'Cameroon', dialCode: '237' },
  { code: 'CF', name: 'Central African Republic', dialCode: '236' },
  { code: 'TD', name: 'Chad', dialCode: '235' },
  { code: 'KM', name: 'Comoros', dialCode: '269' },
  { code: 'CG', name: 'Congo', dialCode: '242' },
  { code: 'CD', name: 'Democratic Republic of the Congo', dialCode: '243' },
  { code: 'CI', name: "Côte d'Ivoire", dialCode: '225' },
  { code: 'DJ', name: 'Djibouti', dialCode: '253' },
  { code: 'EG', name: 'Egypt', dialCode: '20' },
  { code: 'GQ', name: 'Equatorial Guinea', dialCode: '240' },
  { code: 'ER', name: 'Eritrea', dialCode: '291' },
  { code: 'SZ', name: 'Eswatini', dialCode: '268' },
  { code: 'ET', name: 'Ethiopia', dialCode: '251' },
  { code: 'GA', name: 'Gabon', dialCode: '241' },
  { code: 'GM', name: 'Gambia', dialCode: '220' },
  { code: 'GH', name: 'Ghana', dialCode: '233' },
  { code: 'GN', name: 'Guinea', dialCode: '224' },
  { code: 'GW', name: 'Guinea-Bissau', dialCode: '245' },
  { code: 'KE', name: 'Kenya', dialCode: '254' },
  { code: 'LS', name: 'Lesotho', dialCode: '266' },
  { code: 'LR', name: 'Liberia', dialCode: '231' },
  { code: 'LY', name: 'Libya', dialCode: '218' },
  { code: 'MG', name: 'Madagascar', dialCode: '261' },
  { code: 'MW', name: 'Malawi', dialCode: '265' },
  { code: 'ML', name: 'Mali', dialCode: '223' },
  { code: 'MR', name: 'Mauritania', dialCode: '222' },
  { code: 'MU', name: 'Mauritius', dialCode: '230' },
  { code: 'MA', name: 'Morocco', dialCode: '212' },
  { code: 'MZ', name: 'Mozambique', dialCode: '258' },
  { code: 'NA', name: 'Namibia', dialCode: '264' },
  { code: 'NE', name: 'Niger', dialCode: '227' },
  { code: 'NG', name: 'Nigeria', dialCode: '234' },
  { code: 'RW', name: 'Rwanda', dialCode: '250' },
  { code: 'ST', name: 'São Tomé and Príncipe', dialCode: '239' },
  { code: 'SN', name: 'Senegal', dialCode: '221' },
  { code: 'SC', name: 'Seychelles', dialCode: '248' },
  { code: 'SL', name: 'Sierra Leone', dialCode: '232' },
  { code: 'SO', name: 'Somalia', dialCode: '252' },
  { code: 'ZA', name: 'South Africa', dialCode: '27' },
  { code: 'SS', name: 'South Sudan', dialCode: '211' },
  { code: 'SD', name: 'Sudan', dialCode: '249' },
  { code: 'TZ', name: 'Tanzania', dialCode: '255' },
  { code: 'TG', name: 'Togo', dialCode: '228' },
  { code: 'TN', name: 'Tunisia', dialCode: '216' },
  { code: 'UG', name: 'Uganda', dialCode: '256' },
  { code: 'ZM', name: 'Zambia', dialCode: '260' },
  { code: 'ZW', name: 'Zimbabwe', dialCode: '263' },
];

/** What an address with no country is taken to be: everything recorded before countries. */
export const DEFAULT_COUNTRY = 'ZA';

/** South Africa has a fixed province list, a four-digit postcode and expects a suburb. */
export function isSouthAfrica(country: string | null | undefined): boolean {
  return (country || DEFAULT_COUNTRY) === 'ZA';
}

/** A region as a person would read it: a province's name for South Africa, else as typed. */
export function regionDisplay(value: string | null | undefined): string {
  return (value && PROVINCE_LABELS[value as Province]) || value || '';
}

export function countryName(code: string | null | undefined): string {
  return COUNTRIES.find((c) => c.code === (code || DEFAULT_COUNTRY))?.name ?? code ?? '';
}

/**
 * A structured postal address. Street, city and region are always required, so distance-based
 * job matching never has to work around a gap the way a person reading a free-text address
 * could. Suburb and postal code are required only in South Africa; elsewhere they are optional,
 * because many countries have neither.
 */
export interface Address {
  country: string;
  street: string;
  suburb: string;
  city: string;
  /** One of {@link Province} for South Africa; free text (a state, region, county) elsewhere. */
  province: string;
  postalCode: string;
}

/** Same shape as {@link Address}; a distinct alias documents that this is what a form submits,
 * not what the server already has on file. */
export type AddressRequest = Address;
