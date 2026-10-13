import { AddressRequest } from '../../core/address.models';

export type DocumentKind =
  'ID_DOCUMENT' | 'MATRIC_CERTIFICATE' | 'PROOF_OF_RESIDENCE' | 'CERTIFICATE' | 'QUALIFICATION';

export const DOCUMENT_KIND_LABELS: Record<DocumentKind, string> = {
  ID_DOCUMENT: 'ID document',
  MATRIC_CERTIFICATE: 'Matric certificate',
  PROOF_OF_RESIDENCE: 'Proof of residence',
  CERTIFICATE: 'Certification',
  QUALIFICATION: 'Qualification',
};

/** The three documents every application must carry, one of each. */
export interface RequiredDocument {
  readonly kind: DocumentKind;
  readonly label: string;
  /** How it reads in a sentence: "Click here to upload your ID document". */
  readonly short: string;
  readonly hint: string;
}

export const REQUIRED_DOCUMENTS: readonly RequiredDocument[] = [
  {
    kind: 'ID_DOCUMENT',
    label: 'ID document',
    short: 'ID document',
    hint: 'A clear copy of your South African ID card or ID book.',
  },
  {
    kind: 'MATRIC_CERTIFICATE',
    label: 'Matric certificate',
    short: 'matric certificate',
    hint: 'Your National Senior Certificate, or an equivalent.',
  },
  {
    kind: 'PROOF_OF_RESIDENCE',
    label: 'Proof of residence',
    short: 'proof of residence',
    hint: 'A utility bill, bank statement or lease, no more than three months old.',
  },
];

/**
 * Client-side limits. These are for a fast, friendly error only: the server re-checks every one
 * of them (and more, by reading the file's actual bytes), so none of this is a security control.
 */
export const MAX_FILES = 10;
export const MAX_FILE_BYTES = 10 * 1024 * 1024;
export const ACCEPTED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png'] as const;
export const ACCEPT_ATTRIBUTE = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png';

/** The JSON half of the multipart submission; mirrors the backend `SubmitApplicationRequest`. */
export interface SubmitApplicationRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  /** South African ID number, digits only. */
  idNumber: string;
  address: AddressRequest;
  experienceSummary: string;
  skills: string | null;
  documents: { kind: DocumentKind; title: string }[];
  consent: boolean;
  /** Honeypot. Always empty from a real person. */
  website: string;
}

export interface ApplicationReceivedResponse {
  message: string;
}

/** One file the applicant has picked, with what it is proof of. */
export interface PickedDocument {
  /** Local-only key so the list can track and remove entries; never sent anywhere. */
  readonly key: number;
  readonly file: File;
  kind: DocumentKind;
  title: string;
}

export function extensionOf(filename: string): string {
  const dot = filename.lastIndexOf('.');
  return dot < 0 ? '' : filename.slice(dot + 1).toLowerCase();
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024 * 1024) {
    return `${Math.max(1, Math.round(bytes / 1024))} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

/** Why a file can't be attached, or null if it looks fine. */
export function problemWithFile(file: File): string | null {
  if (!(ACCEPTED_EXTENSIONS as readonly string[]).includes(extensionOf(file.name))) {
    return `"${file.name}" isn't a PDF, JPG or PNG.`;
  }
  if (file.size === 0) {
    return `"${file.name}" is empty.`;
  }
  if (file.size > MAX_FILE_BYTES) {
    return `"${file.name}" is ${formatBytes(file.size)} — the limit is ${formatBytes(MAX_FILE_BYTES)}.`;
  }
  return null;
}

/**
 * Whether `value` looks like a South African ID number: 13 digits, a real birth date, a
 * citizenship digit of 0 or 1, and the Luhn check digit. Spaces are ignored. This only catches
 * typos early — the server re-validates it, and no check here can prove the number is real.
 */
export function isValidSouthAfricanId(value: string): boolean {
  const id = value.replace(/\s+/g, '');
  if (!/^\d{13}$/.test(id)) {
    return false;
  }

  const yy = Number(id.slice(0, 2));
  const mm = Number(id.slice(2, 4));
  const dd = Number(id.slice(4, 6));
  const today = new Date();
  const realDate = [1900, 2000].some((century) => {
    const born = new Date(century + yy, mm - 1, dd);
    return (
      born.getFullYear() === century + yy &&
      born.getMonth() === mm - 1 &&
      born.getDate() === dd &&
      born <= today
    );
  });
  if (!realDate || (id[10] !== '0' && id[10] !== '1')) {
    return false;
  }

  let sum = 0;
  for (let i = 0; i < id.length; i++) {
    let digit = Number(id[id.length - 1 - i]);
    if (i % 2 === 1) {
      digit *= 2;
      if (digit > 9) {
        digit -= 9;
      }
    }
    sum += digit;
  }
  return sum % 10 === 0;
}
