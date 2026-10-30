import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { JOB_STATUS_LABELS, JobSummary } from '../../controller/controller.models';
import { IN_HAND } from '../job-steps';
import { TechnicianApiService } from '../technician-api.service';

/**
 * The technician's jobs on their home page: the ones still in hand first, each opening the job
 * screen, then the ones they have recently reported on.
 */
@Component({
  selector: 'app-my-jobs',
  imports: [DatePipe, RouterLink],
  templateUrl: './my-jobs.html',
  styleUrl: './my-jobs.scss',
})
export class MyJobs {
  private readonly api = inject(TechnicianApiService);

  readonly labels = JOB_STATUS_LABELS;
  private readonly all = signal<JobSummary[] | null>(null);
  readonly loadFailed = signal(false);

  readonly inHand = computed(() => (this.all() ?? []).filter((job) => IN_HAND.has(job.status)));
  readonly finished = computed(() => (this.all() ?? []).filter((job) => !IN_HAND.has(job.status)));
  readonly loaded = computed(() => this.all() !== null);

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loadFailed.set(false);
    this.api.myJobs().subscribe({
      next: (jobs) => this.all.set(jobs),
      error: () => this.loadFailed.set(true),
    });
  }
}
