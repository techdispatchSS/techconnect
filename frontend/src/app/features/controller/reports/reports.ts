import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

import { Skeleton } from '../../../shared/skeleton/skeleton';
import { ControllerApiService } from '../controller-api.service';
import { PRIORITY_LABELS, Report } from '../controller.models';

/** A calendar day as `yyyy-MM-dd` in the browser's own zone, which is what the report is in. */
export function isoDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return isoDay(date);
}

/** How the range presets read, and how far back each goes (today inclusive). */
const PRESETS = [
  { label: 'Last 7 days', back: 6 },
  { label: 'Last 30 days', back: 29 },
  { label: 'Last 90 days', back: 89 },
] as const;

/**
 * The controller's performance report: SLA compliance, how quickly work is taken, and what each
 * technician did, for a range of days. Days are the controller's own calendar days — the
 * browser's time zone is sent, so "today" is today for them.
 *
 * <p>A measure with nothing behind it reads "—", never "0%": a quiet week is not a failing one.
 */
@Component({
  selector: 'app-reports',
  imports: [ReactiveFormsModule, Skeleton],
  templateUrl: './reports.html',
  styleUrl: './reports.scss',
})
export class Reports implements OnInit {
  private readonly api = inject(ControllerApiService);

  readonly presets = PRESETS;
  readonly priorityLabels = PRIORITY_LABELS;
  private readonly zone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC';

  readonly from = new FormControl(daysAgo(29), { nonNullable: true });
  readonly to = new FormControl(daysAgo(0), { nonNullable: true });

  readonly report = signal<Report | null>(null);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly errorMessage = signal<string | null>(null);
  readonly exporting = signal(false);

  /** The tallest day, so the bars are drawn relative to it. */
  readonly peak = computed(() => {
    const days = this.report()?.daily ?? [];
    return Math.max(1, ...days.map((d) => Math.max(d.created, d.closed)));
  });

  /** Many days make a crowded axis; label roughly every n-th so it stays readable. */
  readonly labelEvery = computed(() =>
    Math.max(1, Math.ceil((this.report()?.daily.length ?? 1) / 8)),
  );

  ngOnInit(): void {
    this.load();
  }

  usePreset(back: number): void {
    this.from.setValue(daysAgo(back));
    this.to.setValue(daysAgo(0));
    this.load();
  }

  load(): void {
    const from = this.from.value;
    const to = this.to.value;
    if (!from || !to) {
      this.errorMessage.set('Pick a start and an end date.');
      return;
    }
    this.errorMessage.set(null);
    this.loading.set(true);
    this.loadFailed.set(false);
    this.api.report(from, to, this.zone).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (error: { status?: number; error?: { error?: string } }) => {
        this.loading.set(false);
        if (error.status === 400) {
          this.errorMessage.set(error.error?.error ?? 'That range is not valid.');
        } else {
          this.loadFailed.set(true);
        }
      },
    });
  }

  download(): void {
    if (this.exporting()) {
      return;
    }
    this.exporting.set(true);
    this.api.reportCsv(this.from.value, this.to.value, this.zone).subscribe({
      next: (blob) => {
        this.exporting.set(false);
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `techconnect-incidents-${this.from.value}-to-${this.to.value}.csv`;
        link.click();
        // After the click has been handled; revoking at once can cancel the download.
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => {
        this.exporting.set(false);
        this.errorMessage.set("Couldn't export the report. Try again.");
      },
    });
  }

  /** "—" for no data, else the number with its unit. */
  fmt(value: number | null, suffix = ''): string {
    return value === null || value === undefined ? '—' : `${value}${suffix}`;
  }

  /** Minutes, but a fast accept reads "under a minute" rather than a flat "0 min". */
  fmtMinutes(value: number | null): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return value < 1 ? '< 1 min' : `${value} min`;
  }

  /** Hours, but a very short stretch reads "under 6 min" rather than "0 h". */
  fmtHours(value: number | null): string {
    if (value === null || value === undefined) {
      return '—';
    }
    return value < 0.1 ? '< 6 min' : `${value} h`;
  }

  height(count: number): number {
    return Math.round((count / this.peak()) * 100);
  }

  showLabel(index: number): boolean {
    return index % this.labelEvery() === 0;
  }

  dayLabel(iso: string): string {
    const [, month, day] = iso.split('-');
    return `${Number(day)}/${Number(month)}`;
  }
}
