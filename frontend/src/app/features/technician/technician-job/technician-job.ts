import { DatePipe, TitleCasePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnInit, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { filter } from 'rxjs';

import { RealtimeService } from '../../../core/realtime/realtime.service';
import { JOB_STATUS_LABELS, JobStatus } from '../../controller/controller.models';
import { CONFIRM_STEPS, STEP_LABELS, directionsUrl, orderSteps } from '../job-steps';
import { TechnicianApiService } from '../technician-api.service';
import { MyJob } from '../technician.models';

/**
 * One of the technician's jobs (FR-06): who to see and where, and the next step to tap. The
 * buttons are exactly the steps the server says are allowed from here. Going on hold asks for
 * a reason, which the controller sees; marking the work complete asks once more first.
 */
@Component({
  selector: 'app-technician-job',
  imports: [DatePipe, RouterLink, TitleCasePipe],
  templateUrl: './technician-job.html',
  styleUrl: './technician-job.scss',
})
export class TechnicianJob implements OnInit {
  private readonly api = inject(TechnicianApiService);
  private readonly realtime = inject(RealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  /** The route's `:id`, bound by the router. */
  readonly id = input.required<string>();

  readonly labels = JOB_STATUS_LABELS;
  readonly stepLabels = STEP_LABELS;
  readonly job = signal<MyJob | null>(null);
  readonly loadFailed = signal<'missing' | 'error' | null>(null);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  /** A step waiting on the extra input it needs: a reason, or a second tap. */
  readonly pending = signal<JobStatus | null>(null);
  readonly reason = signal('');

  readonly steps = computed(() => orderSteps(this.job()?.nextSteps ?? []));
  readonly directions = computed(() => directionsUrl(this.job()?.detail.job.siteAddress ?? null));

  ngOnInit(): void {
    this.load();
    // The controller may close it while it's on screen.
    this.realtime
      .on('JOB_CHANGED')
      .pipe(
        filter((event) => event.jobId === this.id()),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => this.load());
  }

  load(): void {
    this.loadFailed.set(null);
    this.api.myJob(this.id()).subscribe({
      next: (job) => this.job.set(job),
      error: (error: unknown) =>
        this.loadFailed.set(
          error instanceof HttpErrorResponse && error.status === 404 ? 'missing' : 'error',
        ),
    });
  }

  tap(step: JobStatus): void {
    if (step === 'ON_HOLD' || CONFIRM_STEPS.has(step)) {
      this.error.set(null);
      this.reason.set('');
      this.pending.set(step);
      return;
    }
    this.send(step);
  }

  cancel(): void {
    this.pending.set(null);
  }

  confirm(): void {
    const step = this.pending();
    if (!step) {
      return;
    }
    if (step === 'ON_HOLD' && !this.reason().trim()) {
      this.error.set('Say why the job is being put on hold.');
      return;
    }
    this.send(step, step === 'ON_HOLD' ? this.reason().trim() : undefined);
  }

  private send(step: JobStatus, reason?: string): void {
    if (this.saving()) {
      return;
    }
    this.saving.set(true);
    this.error.set(null);
    this.api.updateJobStatus(this.id(), step, reason).subscribe({
      next: (job) => {
        this.saving.set(false);
        this.pending.set(null);
        this.job.set(job);
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(
          (error instanceof HttpErrorResponse &&
            (error.error as { error?: string } | null)?.error) ||
            "Couldn't update the job. Check your connection and try again.",
        );
        // A 409 means our view was behind: show where the job really is.
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.pending.set(null);
          this.api
            .myJob(this.id())
            .subscribe({ next: (job) => this.job.set(job), error: () => undefined });
        }
      },
    });
  }
}
