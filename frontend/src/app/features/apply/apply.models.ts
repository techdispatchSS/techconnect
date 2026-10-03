import { AddressRequest } from '../../core/address.models';

export type DocumentKind = 'CERTIFICATE' | 'QUALIFICATION';

export const DOCUMENT_KIND_LABELS: Record<DocumentKind, string> = {
  CERTIFICATE: 'Certificate',
  QUALIFICATION: 'Qualification',
};

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
