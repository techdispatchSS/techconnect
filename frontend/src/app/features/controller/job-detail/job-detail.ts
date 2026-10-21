import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { interval } from 'rxjs';

import { Skeleton } from '../../../shared/skeleton/skeleton';
import { ControllerApiService } from '../controller-api.service';
import { JOB_STATUS_LABELS, JobDetail, JobStatus, PRIORITY_LABELS } from '../controller.models';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { JobCountsService } from '../job-counts.service';
import { elapsedLabel, progressOf, slaCountdown, statusTone } from '../job-format';

const REFRESH_INTERVAL_MS = 10_000;

/** What the demo strip can do for the technician from each status, in the order they would. */
const DEMO_STEPS: Partial<Record<JobStatus, { to: JobStatus; label: string }>> = {
  EN_ROUTE: { to: 'ON_SITE', label: 'Arrive on site' },
  ON_SITE: { to: 'WORK_STARTED', label: 'Start work' },
  WORK_STARTED: { to: 'COMPLETE', label: 'Finish work' },
  WORK_RESUMED: { to: 'COMPLETE', label: 'Finish work' },
  ON_HOLD: { to: 'WORK_RESUMED', label: 'Resume work' },
};

/**
 * One job, end to end (FR-05–FR-07): where it stands, who is on it, the timeline of what has
 * happened, and — once the technician has submitted their report — the form to review it and
 * close the job.
 *
 * <p>The demo strip only exists when the backend says simulation is on, and drives the same
 * service the technician app will, so a demo produces the history a real job would.
 */
@Component({
  selector: 'app-job-detail',
  imports: [DatePipe, ReactiveFormsModule, RouterLink, Skeleton],
  templateUrl: './job-detail.html',
  styleUrl: './job-detail.scss',
})
export class JobDetailPage implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly freshdesk = inject(FreshdeskStatusService);
  private readonly counts = inject(JobCountsService);

  private readonly jobId = this.route.snapshot.paramMap.get('jobId')!;

  readonly statusLabels = JOB_STATUS_LABELS;
  readonly priorityLabels = PRIORITY_LABELS;

  readonly detail = signal<JobDetail | null>(null);
  readonly loadFailed = signal(false);
  readonly notFound = signal(false);

  readonly closingNotes = new FormControl('', {
    nonNullable: true,
    validators: [Validators.maxLength(2000)],
  });
  readonly closing = signal(false);
  readonly closeError = signal<string | null>(null);

  readonly simulation = computed(() => this.freshdesk.status()?.simulation === true);
  readonly simBusy = signal(false);
  readonly simError = signal<string | null>(null);
  readonly holdReason = new FormControl('Waiting for site access', { nonNullable: true });

  readonly job = computed(() => this.detail()?.job ?? null);
  readonly tone = computed(() => {
    const job = this.job();
    return job ? statusTone(job.status) : 'moving';
  });
  readonly progress = computed(() => {
    const job = this.job();
    return job ? Math.round(progressOf(job.status) * 100) : 0;
  });
  readonly sla = computed(() => {
    const job = this.job();
    return job ? slaCountdown(job.slaDueAt, job.status === 'CLOSED') : null;
  });
  readonly timeOnJob = computed(() => {
    const job = this.job();
    return job ? elapsedLabel(job.acceptedAt) : '—';
  });
  readonly demoStep = computed(() => {
    const job = this.job();
    if (!job) {
      return null;
    }
    if (job.status === 'COMPLETE') {
      return { to: 'SUBMITTED' as JobStatus, label: 'Submit report' };
    }
    return DEMO_STEPS[job.status] ?? null;
  });
  readonly canHold = computed(() => {
    const status = this.job()?.status;
    return status === 'WORK_STARTED' || status === 'WORK_RESUMED';
  });
  readonly isClosed = computed(() => this.job()?.status === 'CLOSED');

  ngOnInit(): void {
    this.load();
    interval(REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        // A closed job will not change; and a form being filled in must not be reset under the
        // controller, which a quiet refresh of the same values does not do.
        if (!this.isClosed()) {
          this.load();
        }
      });
  }

  close(): void {
    const detail = this.detail();
    if (!detail || !detail.canClose || this.closing() || this.closingNotes.invalid) {
      return;
    }
    this.closing.set(true);
    this.closeError.set(null);
    this.api.closeJob(this.jobId, this.closingNotes.value.trim() || null).subscribe({
      next: (updated) => {
        this.closing.set(false);
        this.detail.set(updated);
        this.counts.refresh();
      },
      error: (error: unknown) => {
        this.closing.set(false);
        this.closeError.set(serverMessage(error) ?? "Couldn't close this job. Try again.");
        this.load();
      },
    });
  }

  /** Demo: the technician takes their next step. */
  advance(): void {
    const step = this.demoStep();
    if (!step || this.simBusy()) {
      return;
    }
    this.simBusy.set(true);
    this.simError.set(null);
    const call =
      step.to === 'SUBMITTED'
        ? this.api.simulateJobReport(this.jobId)
        : this.api.simulateJobStatus(this.jobId, step.to, null);
    this.afterSimulation(call);
  }

  /** Demo: the technician puts the job on hold, with a reason the controller will see. */
  hold(): void {
    if (this.simBusy()) {
      return;
    }
    this.simBusy.set(true);
    this.simError.set(null);
    this.afterSimulation(
      this.api.simulateJobStatus(this.jobId, 'ON_HOLD', this.holdReason.value.trim() || null),
    );
  }

  private afterSimulation(call: ReturnType<ControllerApiService['simulateJobReport']>): void {
    call.subscribe({
      next: (updated) => {
        this.simBusy.set(false);
        this.detail.set(updated);
        this.counts.refresh();
      },
      error: (error: unknown) => {
        this.simBusy.set(false);
        this.simError.set(serverMessage(error) ?? "The demo step didn't go through.");
      },
    });
  }

  private load(): void {
    this.api.getJob(this.jobId).subscribe({
      next: (detail) => {
        this.detail.set(detail);
        this.loadFailed.set(false);
        this.notFound.set(false);
      },
      error: (error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 404) {
          this.notFound.set(true);
        } else if (!this.detail()) {
          this.loadFailed.set(true);
        }
      },
    });
  }
}

function serverMessage(error: unknown): string | null {
  return error instanceof HttpErrorResponse
    ? ((error.error as { error?: string } | null)?.error ?? null)
    : null;
}
