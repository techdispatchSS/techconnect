import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
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
import { MAX_SKILLS, joinSkills, parseSkills } from '../../shared/skill-list';
import { TrimOnBlur } from '../../shared/trim-on-blur';
import { ApplyApiService } from './apply-api.service';
import {
  ACCEPT_ATTRIBUTE,
  DOCUMENT_KIND_LABELS,
  DocumentKind,
  MAX_FILES,
  MAX_FILE_BYTES,
  PickedDocument,
  extensionOf,
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
  /** Whether Google address search is configured; without it the street is plain text. */
  readonly placesEnabled = inject(GooglePlacesService).enabled;

  readonly provinces = PROVINCES;
  readonly provinceLabels = PROVINCE_LABELS;
  readonly kindLabels = DOCUMENT_KIND_LABELS;
  readonly kinds = Object.keys(DOCUMENT_KIND_LABELS) as DocumentKind[];
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
  readonly dragging = signal(false);
  /** Only nag about missing proof once they've tried to submit. */
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
  readonly proofMissing = computed(() => this.triedToSubmit() && this.documents().length === 0);
  readonly untitledDocuments = computed(
    () => this.triedToSubmit() && this.documents().some((d) => d.title.trim() === ''),
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
    consent: [false, [Validators.requiredTrue]],
    // Honeypot: hidden from people, tempting to bots.
    website: [''],
  });

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

  onFilesChosen(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.addFiles(Array.from(input.files ?? []));
    // So choosing the same file again after removing it still fires `change`.
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(true);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragging.set(false);
    this.addFiles(Array.from(event.dataTransfer?.files ?? []));
  }

  private addFiles(files: File[]): void {
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
      const ext = extensionOf(file.name);
      accepted.push({
        key: this.nextKey++,
        file,
        kind: 'CERTIFICATE',
        title: file.name.slice(0, file.name.length - ext.length - 1).slice(0, 200),
      });
    }

    this.fileProblems.set(problems);
    if (accepted.length > 0) {
      this.documents.update((current) => [...current, ...accepted]);
    }
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

  setKind(key: number, value: string): void {
    this.documents.update((current) =>
      current.map((d) => (d.key === key ? { ...d, kind: value as DocumentKind } : d)),
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
    if (this.form.invalid || this.documents().length === 0 || this.untitledDocuments()) {
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
