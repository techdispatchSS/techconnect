import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, input, output, signal } from '@angular/core';
import { FormArray, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { TrimOnBlur } from '../../../shared/trim-on-blur';
import { TechnicianApiService } from '../technician-api.service';
import { JobReport, MyJob } from '../technician.models';

/** The server's limit on parts in one report. */
const MAX_PARTS = 50;
/** Something other than spaces: the server refuses a blank required field. */
const NOT_BLANK = /\S/;

/**
 * The job card the technician submits once the work is complete (FR-06): what was done, why it
 * happened, the hours and any parts used. Submitting locks the job for the controller to review,
 * so a failed attempt keeps everything typed, ready to try again.
 */
@Component({
  selector: 'app-job-report-form',
  imports: [ReactiveFormsModule, TrimOnBlur],
  templateUrl: './job-report-form.html',
  styleUrl: './job-report-form.scss',
})
export class JobReportForm {
  private readonly api = inject(TechnicianApiService);
  private readonly fb = inject(FormBuilder).nonNullable;

  readonly jobId = input.required<string>();
  /** The job as it stands after a successful submit. */
  readonly submitted = output<MyJob>();
  /** The server says this job can't be submitted any more: the page around it is out of date. */
  readonly stale = output<void>();

  readonly maxParts = MAX_PARTS;
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);

  readonly form = this.fb.group({
    resolutionNotes: [
      '',
      [Validators.required, Validators.pattern(NOT_BLANK), Validators.maxLength(4000)],
    ],
    rootCause: [
      '',
      [Validators.required, Validators.pattern(NOT_BLANK), Validators.maxLength(2000)],
    ],
    recommendations: ['', [Validators.maxLength(2000)]],
    labourHours: [
      null as number | null,
      [Validators.required, Validators.min(0), Validators.max(999.99)],
    ],
    parts: this.fb.array<ReturnType<JobReportForm['newPart']>>([]),
  });

  get parts(): FormArray<ReturnType<JobReportForm['newPart']>> {
    return this.form.controls.parts;
  }

  addPart(): void {
    if (this.parts.length < MAX_PARTS) {
      this.parts.push(this.newPart());
    }
  }

  removePart(index: number): void {
    this.parts.removeAt(index);
  }

  /** True once a field has been left wrong, or a submit has shown up that it is. */
  showError(control: { invalid: boolean; touched: boolean }): boolean {
    return control.invalid && control.touched;
  }

  submit(): void {
    if (this.saving()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Fill in the highlighted fields first.');
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.submitReport(this.jobId(), this.toReport()).subscribe({
      next: (job) => {
        this.saving.set(false);
        this.submitted.emit(job);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(
          (error instanceof HttpErrorResponse &&
            (error.error as { error?: string } | null)?.error) ||
            "Couldn't submit the report. Your answers are still here, so try again.",
        );
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.stale.emit();
        }
      },
    });
  }

  private newPart() {
    return this.fb.group({
      name: ['', [Validators.required, Validators.pattern(NOT_BLANK), Validators.maxLength(255)]],
      quantity: [1, [Validators.required, Validators.min(1), Validators.max(10_000)]],
      serialNumber: ['', [Validators.maxLength(128)]],
    });
  }

  private toReport(): JobReport {
    const value = this.form.getRawValue();
    return {
      resolutionNotes: value.resolutionNotes.trim(),
      rootCause: value.rootCause.trim(),
      recommendations: value.recommendations.trim() || null,
      labourHours: value.labourHours as number,
      parts: value.parts.map((part) => ({
        name: part.name.trim(),
        quantity: part.quantity,
        serialNumber: part.serialNumber.trim() || null,
      })),
    };
  }
}
