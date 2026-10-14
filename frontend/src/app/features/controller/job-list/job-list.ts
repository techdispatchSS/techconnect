import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, interval } from 'rxjs';

import { Skeleton } from '../../../shared/skeleton/skeleton';
import { ControllerApiService } from '../controller-api.service';
import { JOB_STATUS_LABELS, JobStage, JobSummary, PRIORITY_LABELS } from '../controller.models';
import { JobCountsService } from '../job-counts.service';
import { elapsedLabel, priorityTone, slaCountdown, statusTone } from '../job-format';

const PAGE_SIZE = 20;
const REFRESH_INTERVAL_MS = 20_000;

const STAGE_LABELS: Record<JobStage, string> = {
  ACTIVE: 'Active',
  REVIEW: 'To review',
  CLOSED: 'Closed',
};

/**
 * The controller's list of jobs in hand (FR-05, FR-07). One component for two screens, told
 * which by route data: Active jobs shows what technicians are working, and Review &amp; close
 * shows reports waiting for sign-off, with the closed ones one tab over.
 *
 * <p>Searching, paging and sorting are the server's. The list quietly re-reads every 20s so a
 * job moving on, or a report arriving, shows up without the controller reloading.
 */
@Component({
  selector: 'app-job-list',
  imports: [ReactiveFormsModule, Skeleton],
  templateUrl: './job-list.html',
  styleUrl: './job-list.scss',
})
export class JobList implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly countsService = inject(JobCountsService);

  readonly stages: JobStage[] = this.route.snapshot.data['stages'] ?? ['ACTIVE'];
  readonly intro: string = this.route.snapshot.data['intro'] ?? '';
  readonly stageLabels = STAGE_LABELS;
  readonly statusLabels = JOB_STATUS_LABELS;
  readonly priorityLabels = PRIORITY_LABELS;
  readonly skeletonRows = [0, 1, 2, 3, 4];

  readonly stage = signal<JobStage>(this.stages[0]);
  readonly search = new FormControl('', { nonNullable: true });
  readonly page = signal(0);

  readonly jobs = signal<JobSummary[]>([]);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);

  readonly counts = this.countsService.counts;
  readonly tabCount = computed(() => {
    const c = this.counts();
    return c ? { ACTIVE: c.active, REVIEW: c.review, CLOSED: c.closed } : null;
  });

  readonly rangeLabel = computed(() => {
    const total = this.totalElements();
    if (total === 0) {
      return '';
    }
    const from = this.page() * PAGE_SIZE + 1;
    return `${from}–${Math.min(total, from + PAGE_SIZE - 1)} of ${total}`;
  });

  /** Bumped on every read so a slow answer cannot overwrite a newer one. */
  private requestId = 0;

  ngOnInit(): void {
    this.search.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.page.set(0);
        this.load(true);
      });
    interval(REFRESH_INTERVAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load(false));
    this.load(true);
  }

  selectStage(stage: JobStage): void {
    if (stage === this.stage()) {
      return;
    }
    this.stage.set(stage);
    this.page.set(0);
    this.load(true);
  }

  goTo(page: number): void {
    this.page.set(page);
    this.load(true);
  }

  open(job: JobSummary): void {
    void this.router.navigate(['/incidents/jobs', job.id]);
  }

  tone(job: JobSummary): string {
    return `jl-status jl-status--${statusTone(job.status)}`;
  }

  priorityClass(job: JobSummary): string {
    return priorityTone(job.priority);
  }

  /** What time column to show for the stage: how long it has been going, or when it ended. */
  since(job: JobSummary): string {
    return job.status === 'CLOSED'
      ? elapsedLabel(job.closedAt) + ' ago'
      : elapsedLabel(job.acceptedAt);
  }

  sla(job: JobSummary): { text: string; overdue: boolean } | null {
    return slaCountdown(job.slaDueAt, job.status === 'CLOSED');
  }

  private load(showLoading: boolean): void {
    const id = ++this.requestId;
    if (showLoading) {
      this.loading.set(true);
    }
    this.api.listJobs(this.stage(), this.search.value, this.page(), PAGE_SIZE).subscribe({
      next: (result) => {
        if (id !== this.requestId) {
          return;
        }
        this.jobs.set(result.content);
        this.totalElements.set(result.totalElements);
        this.totalPages.set(result.totalPages);
        this.loading.set(false);
        this.loadFailed.set(false);
        this.countsService.refresh();
      },
      error: () => {
        if (id !== this.requestId) {
          return;
        }
        this.loading.set(false);
        // A failed quiet refresh keeps what is on screen rather than replacing it with an error.
        if (showLoading || this.jobs().length === 0) {
          this.loadFailed.set(true);
        }
      },
    });
  }
}
