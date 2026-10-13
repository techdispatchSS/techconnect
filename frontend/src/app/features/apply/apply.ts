import { HttpErrorResponse } from '@angular/common/http';
import { Component, Injector, afterNextRender, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';

import { PROVINCES, PROVINCE_LABELS, Province } from '../../core/address.models';
import { GooglePlacesService } from '../../core/google/google-places.service';
import { requiredTrimmed, trimStrings } from '../../shared/form-text';
import { TrimOnBlur } from '../../shared/trim-on-blur';
import { ApplyApiService } from './apply-api.service';
import {
  ACCEPT_ATTRIBUTE,
  DocumentKind,
  MAX_FILES,
  MAX_FILE_BYTES,
  PickedDocument,
  REQUIRED_DOCUMENTS,
  formatBytes,
  isValidSouthAfricanId,
  problemWithFile,
} from './apply.models';
import { InstallButton } from '../../shared/install-button/install-button';
import { Logo } from '../../shared/logo/logo';
import { PickedPlace, StreetInput } from '../../shared/street-input/street-input';

/**
 * Runs a validator against the trimmed value. Pressing Enter submits without the field ever
 * losing focus, so the blur-time trim hasn't happened yet; without this a stray leading space
 * would report a misleading "letters only" error for a perfectly good name.
 */
function onTrimmed(validator: ValidatorFn): ValidatorFn {
  return (control: AbstractControl) => {
    const value: unknown = control.value;
    return validator({
      value: typeof value === 'string' ? value.trim() : value,
    } as AbstractControl);
  };
}

const NAME_PATTERN = onTrimmed(Validators.pattern(/^[\p{L}][\p{L} .'’-]*$/u));

/** Judges the ID number with spaces ignored, so "900101 5000 085" is accepted as typed. */
const SA_ID: ValidatorFn = (control) =>
  typeof control.value === 'string' &&
  control.value.trim() !== '' &&
  !isValidSouthAfricanId(control.value)
    ? { saId: true }
    : null;

/**
 * Public technician application. Deliberately not part of the sign-in screens: there is no
 * account behind it, no token is issued, and nothing here can be used to find out whether a
 * person has applied before — a repeat submission gets exactly the same answer as a first one.
 *
 * <p>Every check on this page is a convenience. The server independently re-validates all of it
 * (and inspects the files' real contents), so none of it is relied on for safety.
 */
@Component({
  selector: 'app-apply',
  imports: [ReactiveFormsModule, RouterLink, TrimOnBlur, Logo, StreetInput, InstallButton],
  templateUrl: './apply.html',
  styleUrls: ['../auth/auth-page.scss', './apply.scss'],
})
export class Apply {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApplyApiService);
  private readonly injector = inject(Injector);
  /** Whether Google address search is configured; without it the street is plain text. */
  readonly placesEnabled = inject(GooglePlacesService).enabled;

  readonly provinces = PROVINCES;
  readonly provinceLabels = PROVINCE_LABELS;
  readonly required = REQUIRED_DOCUMENTS;
  readonly accept = ACCEPT_ATTRIBUTE;
  readonly maxFiles = MAX_FILES;
  readonly maxFileSize = formatBytes(MAX_FILE_BYTES);
  readonly formatBytes = formatBytes;

  readonly submitting = signal(false);
  readonly submitted = signal<string | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly documents = signal<PickedDocument[]>([]);
  readonly fileProblems = signal<string[]>([]);
  /** Which upload area a file is being dragged over: a required kind, or 'CERTIFICATION'. */
  readonly dragging = signal<string | null>(null);
  /** Only nag about missing documents once they've tried to submit. */
  readonly triedToSubmit = signal(false);
  private nextKey = 1;

  /** Fills the rest of the address from a Google suggestion. Anything Google didn't return is
   * left as it was, so a person can complete it by hand. */
  applyPlace(place: PickedPlace): void {
    const group = this.form.controls.address;
    for (const [key, value] of Object.entries(place)) {
      const control = group.get(key);
      if (value && control) {
        control.setValue(value);
        control.markAsTouched();
      }
    }
  }

  readonly canAddMore = computed(() => this.documents().length < MAX_FILES);

  /** The ID, matric and proof-of-residence files the applicant has picked, by kind. */
  readonly requiredDocs = computed(() => {
    const byKind = new Map<DocumentKind, PickedDocument>();
    for (const doc of this.documents()) {
      if (REQUIRED_DOCUMENTS.some((r) => r.kind === doc.kind)) {
        byKind.set(doc.kind, doc);
      }
    }
    return byKind;
  });
  /** The optional, applicant-named certifications, in the order they were added. */
  readonly certifications = computed(() =>
    this.documents().filter((d) => d.kind === 'CERTIFICATE'),
  );

  private readonly missingRequired = computed(() =>
    REQUIRED_DOCUMENTS.filter((r) => !this.requiredDocs().has(r.kind)).map((r) => r.kind),
  );
  /** Required documents still to upload; only reported after a submit attempt. */
  readonly missingKinds = computed(() => (this.triedToSubmit() ? this.missingRequired() : []));
  /** A certification that has been uploaded but not yet given a name. */
  readonly untitledCertifications = computed(() =>
    this.certifications().filter((d) => d.title.trim() === ''),
  );

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [requiredTrimmed, Validators.maxLength(100), NAME_PATTERN]],
    lastName: ['', [requiredTrimmed, Validators.maxLength(100), NAME_PATTERN]],
    email: ['', [Validators.required, onTrimmed(Validators.email), Validators.maxLength(254)]],
    idNumber: ['', [Validators.required, Validators.maxLength(20), SA_ID]],
    phone: [
      '',
      [Validators.required, onTrimmed(Validators.pattern(/^\+?[0-9][0-9 ()-]{7,18}[0-9]$/))],
    ],
    address: this.fb.nonNullable.group({
      street: ['', [requiredTrimmed, Validators.maxLength(255)]],
      suburb: ['', [requiredTrimmed, Validators.maxLength(120)]],
      city: ['', [requiredTrimmed, Validators.maxLength(120)]],
      province: ['' as Province | '', [Validators.required]],
      postalCode: ['', [Validators.required, onTrimmed(Validators.pattern(/^\d{4}$/))]],
    }),
    experienceSummary: [
      '',
      [requiredTrimmed, Validators.minLength(20), Validators.maxLength(4000)],
    ],
    skills: ['', [Validators.maxLength(1000)]],
    consent: [false, [Validators.requiredTrue]],
    // Honeypot: hidden from people, tempting to bots.
    website: [''],
  });

  // --- documents -------------------------------------------------------------------------

  /** One of the three required documents: choosing again replaces the earlier file. */
  onRequiredChosen(kind: DocumentKind, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.setRequired(kind, Array.from(input.files ?? [])[0]);
    // So choosing the same file again after removing it still fires `change`.
    input.value = '';
  }

  /** A certification: each file picked becomes its own entry, waiting to be named. */
  onCertificationChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addCertifications(Array.from(input.files ?? []));
    input.value = '';
  }

  onDragOver(event: DragEvent, target: string): void {
    event.preventDefault();
    this.dragging.set(target);
  }

  onDropRequired(event: DragEvent, kind: DocumentKind): void {
    event.preventDefault();
    this.dragging.set(null);
    this.setRequired(kind, Array.from(event.dataTransfer?.files ?? [])[0]);
  }

  onDropCertification(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(null);
    this.addCertifications(Array.from(event.dataTransfer?.files ?? []));
  }

  private setRequired(kind: DocumentKind, file: File | undefined): void {
    if (!file) {
      return;
    }
    const problem = problemWithFile(file);
    if (problem) {
      this.fileProblems.set([problem]);
      return;
    }
    this.fileProblems.set([]);
    const label = REQUIRED_DOCUMENTS.find((r) => r.kind === kind)!.label;
    this.documents.update((current) => [
      ...current.filter((d) => d.kind !== kind),
      { key: this.nextKey++, file, kind, title: label },
    ]);
  }

  private addCertifications(files: File[]): void {
    const problems: string[] = [];
    const accepted: PickedDocument[] = [];

    for (const file of files) {
      if (this.documents().length + accepted.length >= MAX_FILES) {
        problems.push(`You can attach at most ${MAX_FILES} files.`);
        break;
      }
      const problem = problemWithFile(file);
      if (problem) {
        problems.push(problem);
        continue;
      }
      // Left unnamed on purpose: the applicant is asked what it is, rather than being handed a
      // file name like "scan0042" to accept.
      accepted.push({ key: this.nextKey++, file, kind: 'CERTIFICATE', title: '' });
    }

    this.fileProblems.set(problems);
    if (accepted.length === 0) {
      return;
    }
    this.documents.update((current) => [...current, ...accepted]);

    // Move the cursor to the name, so the next thing typed is what the certification is called.
    const first = accepted[0].key;
    afterNextRender(() => document.getElementById('ap-doc-title-' + first)?.focus(), {
      injector: this.injector,
    });
  }

  removeDocument(key: number): void {
    this.documents.update((current) => current.filter((d) => d.key !== key));
    this.fileProblems.set([]);
  }

  setTitle(key: number, value: string): void {
    this.documents.update((current) =>
      current.map((d) => (d.key === key ? { ...d, title: value } : d)),
    );
  }

  // --- submit ----------------------------------------------------------------------------

  submit(): void {
    this.triedToSubmit.set(true);
    if (this.submitting()) {
      return;
    }
    if (
      this.form.invalid ||
      this.missingRequired().length > 0 ||
      this.untitledCertifications().length > 0
    ) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = this.form.getRawValue();
    const docs = this.documents();

    this.api
      .submit(
        trimStrings({
          firstName: value.firstName,
          lastName: value.lastName,
          email: value.email,
          phone: value.phone,
          idNumber: value.idNumber.replace(/\s+/g, ''),
          address: { ...value.address, province: value.address.province as Province },
          experienceSummary: value.experienceSummary,
          skills: value.skills.trim() || null,
          documents: docs.map((d) => ({ kind: d.kind, title: d.title })),
          consent: value.consent,
          website: value.website,
        }),
        docs.map((d) => d.file),
      )
      .subscribe({
        next: (response) => {
          this.submitting.set(false);
          this.submitted.set(response.message);
          window.scrollTo({ top: 0 });
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.describe(error));
        },
      });
  }

  private describe(error: unknown): string {
    if (!(error instanceof HttpErrorResponse)) {
      return 'Something went wrong. Please try again.';
    }
    const server = (error.error as { error?: string } | null)?.error;
    switch (error.status) {
      case 400:
      case 413:
        return server ?? 'Please check your details and files, then try again.';
      case 429:
        return server ?? 'Too many applications from your connection. Please try again later.';
      case 0:
      case 503:
        // The error interceptor already announces these; keep the form's own message calm.
        return 'We couldn’t send your application. Please check your connection and try again.';
      default:
        return 'Something went wrong. Please try again.';
    }
  }
}
