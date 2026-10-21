import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';

import { bindAddressRules } from '../../core/address-rules';
import { COUNTRIES, PROVINCES, PROVINCE_LABELS } from '../../core/address.models';
import { GooglePlacesService } from '../../core/google/google-places.service';
import { requiredTrimmed, trimStrings } from '../../shared/form-text';
import { MAX_SKILLS, joinSkills, parseSkills } from '../../shared/skill-list';
import { TrimOnBlur } from '../../shared/trim-on-blur';
import { ApplyApiService } from './apply-api.service';
import {
  ACCEPT_ATTRIBUTE,
  DocumentKind,
  MAX_FILES,
  MAX_FILES_PER_REQUIRED,
  MAX_FILE_BYTES,
  PickedDocument,
  REQUIRED_DOCUMENTS,
  formatBytes,
  isValidSouthAfricanId,
  problemWithFile,
} from './apply.models';
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

/**
 * Judges the ID number with spaces ignored, so "900101 5000 085" is accepted as typed. Only a
 * South African applicant is held to the South African format; everywhere else the numbers
 * (national ID, passport) vary too much for anything stricter than "looks like an identifier":
 * letters and digits with the separators real ones use, such as 63-123456 A 12 or AB/1234/56.
 */
function idNumberRule(country: () => string): ValidatorFn {
  return (control) => {
    const value: unknown = control.value;
    if (typeof value !== 'string' || value.trim() === '') {
      return null;
    }
    if (country() === 'ZA') {
      return isValidSouthAfricanId(value) ? null : { saId: true };
    }
    return OTHER_ID.test(value.trim()) ? null : { otherId: true };
  };
}

const OTHER_ID = /^[\p{L}\p{N}][\p{L}\p{N} /.:#()_-]{1,30}[\p{L}\p{N}]$/u;

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
  imports: [ReactiveFormsModule, RouterLink, TrimOnBlur, Logo, StreetInput],
  templateUrl: './apply.html',
  styleUrls: ['../auth/auth-page.scss', './apply.scss'],
})
export class Apply {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(ApplyApiService);
  private readonly injector = inject(Injector);
  /** Whether Google address search is configured; without it the street is plain text. */
  readonly placesEnabled = inject(GooglePlacesService).enabled;

  private readonly destroyRef = inject(DestroyRef);
  readonly countries = COUNTRIES;
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

  // --- skills: one per row ---------------------------------------------------------------
  readonly maxSkills = MAX_SKILLS;
  readonly skills = signal<string[]>([]);
  /** What has been typed in the box but not yet added to the list. */
  readonly skillDraft = signal('');
  readonly skillsFull = computed(() => this.skills().length >= MAX_SKILLS);
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

  /** The identification, diploma and proof-of-residence files picked, by kind; each has one or more. */
  readonly requiredDocs = computed(() => {
    const byKind = new Map<DocumentKind, PickedDocument[]>();
    for (const doc of this.documents()) {
      if (REQUIRED_DOCUMENTS.some((r) => r.kind === doc.kind)) {
        byKind.set(doc.kind, [...(byKind.get(doc.kind) ?? []), doc]);
      }
    }
    return byKind;
  });
  /** Problems with the files picked for a required document, by kind. */
  readonly requiredProblems = signal<Partial<Record<DocumentKind, string[]>>>({});
  /** The optional, applicant-named certifications, in the order they were added. */
  readonly certifications = computed(() =>
    this.documents().filter((d) => d.kind === 'CERTIFICATE'),
  );

  private readonly missingRequired = computed(() =>
    REQUIRED_DOCUMENTS.filter((r) => !this.requiredDocs().get(r.kind)?.length).map((r) => r.kind),
  );
  /** Required documents still to upload; only reported after a submit attempt. */
  readonly missingKinds = computed(() => (this.triedToSubmit() ? this.missingRequired() : []));
  /** A certification that has been uploaded but not yet given a name. */
  readonly untitledCertifications = computed(() =>
    this.certifications().filter((d) => d.title.trim() === ''),
  );

  /**
   * Held outside the form so the ID number's rule can read it as it is validated. Empty until the
   * applicant chooses: nobody is assumed to be South African, and until they choose the form asks
   * only for what is the same everywhere.
   */
  private readonly countryControl = this.fb.nonNullable.control('', [Validators.required]);

  readonly form = this.fb.nonNullable.group({
    firstName: ['', [requiredTrimmed, Validators.maxLength(100), NAME_PATTERN]],
    lastName: ['', [requiredTrimmed, Validators.maxLength(100), NAME_PATTERN]],
    email: ['', [Validators.required, onTrimmed(Validators.email), Validators.maxLength(254)]],
    idNumber: [
      '',
      [
        Validators.required,
        Validators.maxLength(32),
        idNumberRule(() => this.countryControl.value),
      ],
    ],
    phone: [
      '',
      [Validators.required, onTrimmed(Validators.pattern(/^\+?[0-9][0-9 ()-]{7,18}[0-9]$/))],
    ],
    // `country` leads the group: a suggestion sets its fields in this order, so the country is
    // in place before the region (and changing country clears the region).
    address: this.fb.nonNullable.group({
      country: this.countryControl,
      street: ['', [requiredTrimmed, Validators.maxLength(255)]],
      suburb: ['', [requiredTrimmed, Validators.maxLength(120)]],
      city: ['', [requiredTrimmed, Validators.maxLength(120)]],
      province: ['', [Validators.required]],
      postalCode: ['', [Validators.required]],
    }),
    experienceSummary: [
      '',
      [requiredTrimmed, Validators.minLength(20), Validators.maxLength(4000)],
    ],
    consent: [false, [Validators.requiredTrue]],
    // Honeypot: hidden from people, tempting to bots.
    website: [''],
  });

  /** The chosen country, as a signal for the template. */
  readonly country = toSignal(this.countryControl.valueChanges, {
    initialValue: this.countryControl.value,
  });
  readonly inSouthAfrica = computed(() => this.country() === 'ZA');
  readonly countryChosen = computed(() => this.country() !== '');
  /** The calling code of the chosen country, so the phone number can be asked for with it. */
  readonly dialCode = computed(
    () => COUNTRIES.find((c) => c.code === this.country())?.dialCode ?? '',
  );

  constructor() {
    bindAddressRules(this.form.controls.address, this.destroyRef);
    this.countryControl.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.form.controls.idNumber.updateValueAndValidity());
  }

  /** Enter or a comma adds the skill typed so far; Enter must not submit the whole form. */
  onSkillKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault();
      this.addSkill();
    }
  }

  /** Moves what is in the box onto the list. A pasted "CCTV, fibre, cabling" becomes three rows. */
  addSkill(): void {
    const additions = parseSkills(this.skillDraft());
    this.skillDraft.set('');
    if (additions.length === 0) {
      return;
    }
    this.skills.update((current) => {
      const known = new Set(current.map((s) => s.toLowerCase()));
      const merged = [...current];
      for (const skill of additions) {
        if (merged.length >= MAX_SKILLS) {
          break;
        }
        if (!known.has(skill.toLowerCase())) {
          known.add(skill.toLowerCase());
          merged.push(skill);
        }
      }
      return merged;
    });
  }

  removeSkill(index: number): void {
    this.skills.update((current) => current.filter((_, i) => i !== index));
  }

  // --- documents -------------------------------------------------------------------------

  /** Whether another file can still go with a required document. */
  canAddTo(kind: DocumentKind): boolean {
    return (
      (this.requiredDocs().get(kind)?.length ?? 0) < MAX_FILES_PER_REQUIRED &&
      this.documents().length < MAX_FILES
    );
  }

  /** One of the three required documents: every file picked goes with it, up to the limit. */
  onRequiredChosen(kind: DocumentKind, event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addRequired(kind, Array.from(input.files ?? []));
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
    this.addRequired(kind, Array.from(event.dataTransfer?.files ?? []));
  }

  onDropCertification(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(null);
    this.addCertifications(Array.from(event.dataTransfer?.files ?? []));
  }

  private addRequired(kind: DocumentKind, files: File[]): void {
    if (files.length === 0) {
      return;
    }
    const label = REQUIRED_DOCUMENTS.find((r) => r.kind === kind)!.label;
    const problems: string[] = [];
    const accepted: PickedDocument[] = [];
    const already = this.requiredDocs().get(kind)?.length ?? 0;

    for (const file of files) {
      if (already + accepted.length >= MAX_FILES_PER_REQUIRED) {
        problems.push(
          `You can add up to ${MAX_FILES_PER_REQUIRED} files for your ${label.toLowerCase()}.`,
        );
        break;
      }
      if (this.documents().length + accepted.length >= MAX_FILES) {
        problems.push(`You can attach at most ${MAX_FILES} files.`);
        break;
      }
      const problem = problemWithFile(file);
      if (problem) {
        problems.push(problem);
        continue;
      }
      accepted.push({ key: this.nextKey++, file, kind, title: label });
    }

    this.requiredProblems.update((current) => ({ ...current, [kind]: problems }));
    if (accepted.length > 0) {
      this.documents.update((current) => [...current, ...accepted]);
    }
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
    this.requiredProblems.set({});
  }

  setTitle(key: number, value: string): void {
    this.documents.update((current) =>
      current.map((d) => (d.key === key ? { ...d, title: value } : d)),
    );
  }

  // --- submit ----------------------------------------------------------------------------

  submit(): void {
    // Whatever is still in the skill box counts: it would be a shame to lose a typed skill.
    this.addSkill();
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
          idNumber: value.idNumber.replace(/\s+/g, '').toUpperCase(),
          address: value.address,
          experienceSummary: value.experienceSummary,
          skills: joinSkills(this.skills()),
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
