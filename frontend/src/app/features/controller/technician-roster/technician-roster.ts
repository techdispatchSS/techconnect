import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, interval } from 'rxjs';

import { countryName, regionDisplay } from '../../../core/address.models';
import { Skeleton } from '../../../shared/skeleton/skeleton';
import { ControllerApiService } from '../controller-api.service';
import {
  JOB_STATUS_LABELS,
  TechnicianAvailability,
  TechnicianRosterEntry,
} from '../controller.models';

const PAGE_SIZE = 20;
const REFRESH_INTERVAL_MS = 30_000;

const AVAILABILITY_LABELS: Record<TechnicianAvailability, string> = {
  AVAILABLE: 'Available',
  ON_JOB: 'On a job',
  OFFLINE: 'Offline',
};

/**
 * The controller's read-only roster of technicians: who is free, who is on a job and what it
 * is, and what each can do. Nothing here changes a technician — adding, editing and
 * offboarding are the Manager's — so it has no actions beyond opening the job they are on.
 */
@Component({
  selector: 'app-technician-roster',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, Skeleton],
  templateUrl: './technician-roster.html',
  styleUrl: './technician-roster.scss',
})
export class TechnicianRoster implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly availabilityLabels = AVAILABILITY_LABELS;
  readonly availabilities: TechnicianAvailability[] = ['AVAILABLE', 'ON_JOB', 'OFFLINE'];
  readonly jobStatusLabels = JOB_STATUS_LABELS;
  readonly skeletonRows = [0, 1, 2, 3, 4];

  readonly search = new FormControl('', { nonNullable: true });
  readonly availability = signal<TechnicianAvailability | ''>('');
  readonly page = signal(0);

  readonly technicians = signal<TechnicianRosterEntry[]>([]);
  readonly totalElements = signal(0);
  readonly totalPages = signal(0);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);

  readonly rangeLabel = computed(() => {
    const total = this.totalElements();
    if (total === 0) {
      return '';
    }
    const from = this.page() * PAGE_SIZE + 1;
    return `${from}–${Math.min(total, from + PAGE_SIZE - 1)} of ${total}`;
  });

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

  setAvailability(value: string): void {
    this.availability.set(value as TechnicianAvailability | '');
    this.page.set(0);
    this.load(true);
  }

  goTo(page: number): void {
    this.page.set(page);
    this.load(true);
  }

  where(t: TechnicianRosterEntry): string {
    const parts = [t.city, regionDisplay(t.region)].filter((p) => !!p);
    const country = t.country && t.country !== 'ZA' ? countryName(t.country) : '';
    return [...parts, country].filter((p) => !!p).join(', ') || '—';
  }

  private load(showLoading: boolean): void {
    const id = ++this.requestId;
    if (showLoading) {
      this.loading.set(true);
    }
    this.api
      .listTechnicians(this.search.value, this.availability(), this.page(), PAGE_SIZE)
      .subscribe({
        next: (result) => {
          if (id !== this.requestId) {
            return;
          }
          this.technicians.set(result.content);
          this.totalElements.set(result.totalElements);
          this.totalPages.set(result.totalPages);
          this.loading.set(false);
          this.loadFailed.set(false);
        },
        error: () => {
          if (id !== this.requestId) {
            return;
          }
          this.loading.set(false);
          if (showLoading || this.technicians().length === 0) {
            this.loadFailed.set(true);
          }
        },
      });
  }
}
