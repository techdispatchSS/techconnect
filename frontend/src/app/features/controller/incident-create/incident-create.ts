import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { requiredTrimmed, trimStrings } from '../../../shared/form-text';
import { PickedPlace, StreetInput } from '../../../shared/street-input/street-input';
import { TrimOnBlur } from '../../../shared/trim-on-blur';
import { ControllerApiService } from '../controller-api.service';
import { IncidentPriority, PRIORITY_LABELS } from '../controller.models';

/** Response targets a Controller can pick; the server derives one from the priority otherwise. */
export const SLA_HOUR_OPTIONS = [1, 2, 4, 8, 24, 48] as const;

/** What the priority alone would set the response target to — shown as the form's hint. */
export const DEFAULT_SLA_HOURS: Record<IncidentPriority, number> = {
  HIGH: 4,
  MEDIUM: 8,
  LOW: 24,
};

/** Common fault categories, offered as suggestions; anything can still be typed. */
export const ISSUE_TYPES = [
  'Networking',
  'CCTV',
  'Wi-Fi',
  'Fibre',
  'Power / UPS',
  'Access control',
  'Server room cooling',
  'Other',
] as const;

/** The same shape the server accepts for a contact number. */
const PHONE_PATTERN = /^[0-9+()\- ]*$/;

/**
 * Logging an incident by hand — a client phoned in. The result is an ordinary NEW incident in
 * the queue, indistinguishable from one that arrived from Freshdesk apart from having no ticket
 * number. The server re-validates everything here.
 */
@Component({
  selector: 'app-incident-create',
  imports: [ReactiveFormsModule, RouterLink, StreetInput, TrimOnBlur],
  templateUrl: './incident-create.html',
  styleUrl: './incident-create.scss',
})
export class IncidentCreate {
  private readonly api = inject(ControllerApiService);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);

  readonly priorities: IncidentPriority[] = ['HIGH', 'MEDIUM', 'LOW'];
  readonly priorityLabels = PRIORITY_LABELS;
  readonly slaOptions = SLA_HOUR_OPTIONS;
  readonly issueTypes = ISSUE_TYPES;
  readonly defaultSlaHours = DEFAULT_SLA_HOURS;

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);
  /** Where Google placed the chosen address; null for a hand-typed one. */
  readonly coordinates = signal<{ latitude: number; longitude: number } | null>(null);
  private pickedAddress: string | null = null;

  readonly form = this.fb.nonNullable.group({
    clientName: ['', [requiredTrimmed, Validators.maxLength(255)]],
    siteAddress: ['', [Validators.maxLength(500)]],
    contactName: ['', [Validators.maxLength(255)]],
    contactPhone: ['', [Validators.maxLength(32), Validators.pattern(PHONE_PATTERN)]],
    issueType: ['', [Validators.maxLength(128)]],
    priority: ['MEDIUM' as IncidentPriority, [Validators.required]],
    // '' = "use the default for this priority".
    slaHours: [''],
    description: ['', [Validators.maxLength(4000)]],
    additionalNotes: ['', [Validators.maxLength(4000)]],
  });

  constructor() {
    // A pin that was placed for one address must not stay attached to another: once the
    // person edits the address by hand, the coordinates no longer describe it.
    this.form.controls.siteAddress.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        if (this.coordinates() && value !== this.pickedAddress) {
          this.coordinates.set(null);
        }
      });
  }

  /** The default response target for the priority currently chosen, for the hint text. */
  get defaultHint(): number {
    return DEFAULT_SLA_HOURS[this.form.controls.priority.value];
  }

  setPriority(priority: IncidentPriority): void {
    this.form.controls.priority.setValue(priority);
  }

  onAddressPicked(place: PickedPlace): void {
    const text = [place.street, place.suburb, place.city, place.postalCode]
      .filter((part) => part.trim() !== '')
      .join(', ');
    this.pickedAddress = text;
    this.form.controls.siteAddress.setValue(text);
    this.coordinates.set(
      place.latitude !== null && place.longitude !== null
        ? { latitude: place.latitude, longitude: place.longitude }
        : null,
    );
  }

  submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    const value = trimStrings(this.form.getRawValue());
    const coords = this.coordinates();
    const orNull = (text: string): string | null => (text === '' ? null : text);

    this.api
      .createIncident({
        clientName: value.clientName,
        siteAddress: orNull(value.siteAddress),
        siteLatitude: coords?.latitude ?? null,
        siteLongitude: coords?.longitude ?? null,
        contactName: orNull(value.contactName),
        contactPhone: orNull(value.contactPhone),
        issueType: orNull(value.issueType),
        priority: value.priority,
        slaHours: value.slaHours === '' ? null : Number(value.slaHours),
        description: orNull(value.description),
        additionalNotes: orNull(value.additionalNotes),
      })
      .subscribe({
        next: (incident) => {
          void this.router.navigate(['/incidents/queue'], {
            queryParams: { selected: incident.id },
          });
        },
        error: (error: unknown) => {
          this.submitting.set(false);
          this.errorMessage.set(this.describe(error));
        },
      });
  }

  private describe(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 400) {
      const server = (error.error as { error?: string } | null)?.error;
      if (server && server !== 'Validation failed') {
        return server;
      }
      return 'Some details are not valid. Check the highlighted fields and try again.';
    }
    return "Couldn't log the incident. Try again.";
  }
}
