import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged, interval, startWith } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import {
  Incident,
  IncidentKpis,
  PRIORITY_LABELS,
  QUEUE_TABS,
  QueueTab,
} from '../controller.models';

/** Matches the design mockup's auto-refresh cadence for the incident queue. */
const REFRESH_INTERVAL_MS = 30_000;
const PAGE_SIZE = 20;

@Component({
  selector: 'app-incident-queue',
  imports: [DatePipe, ReactiveFormsModule],
  templateUrl: './incident-queue.html',
  styleUrl: './incident-queue.scss',
})
export class IncidentQueue implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly tabs = QUEUE_TABS;
  readonly priorityLabels = PRIORITY_LABELS;

  readonly activeTab = signal<QueueTab>('New');
  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly incidents = signal<Incident[]>([]);
  readonly kpis = signal<IncidentKpis | null>(null);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly selectedId = signal<string | null>(null);

  readonly selected = computed<Incident | null>(() => {
    const id = this.selectedId();
    const list = this.incidents();
    return list.find((incident) => incident.id === id) ?? (list.length > 0 ? list[0] : null);
  });

  readonly tabCount = computed(() => {
    const kpis = this.kpis();
    if (!kpis) {
      return {} as Record<QueueTab, number | null>;
    }
    return {
      New: kpis.newCount,
      Unassigned: kpis.unassignedCount,
      'In progress': kpis.inProgressCount,
      Overdue: kpis.overdueCount,
    } satisfies Record<QueueTab, number>;
  });

  ngOnInit(): void {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());

    // Auto-refresh: the controller dashboard is a live operational queue, not a page you
    // reload — `startWith(0)` fires the first load immediately, then every 30s after.
    interval(REFRESH_INTERVAL_MS)
      .pipe(startWith(0), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());

    this.api.kpis().subscribe({ next: (kpis) => this.kpis.set(kpis) });
  }

  selectTab(tab: QueueTab): void {
    this.activeTab.set(tab);
    this.load();
  }

  select(incident: Incident): void {
    this.selectedId.set(incident.id);
  }

  createDispatch(): void {
    const incident = this.selected();
    if (incident) {
      this.router.navigate(['/incidents/dispatch', incident.id]);
    }
  }

  /** Minutes since an incident was logged, matching the queue's "Age" column. */
  ageLabel(incident: Incident): string {
    const minutes = Math.max(0, Math.round((Date.now() - new Date(incident.createdAt).getTime()) / 60_000));
    if (minutes < 60) {
      return `${minutes}m`;
    }
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    return remainder === 0 ? `${hours}h` : `${hours}h ${remainder}m`;
  }

  /** SLA countdown for the detail panel — "Overdue by 20m" or "3h 55m remaining". */
  slaCountdown(incident: Incident): string | null {
    if (!incident.slaDueAt) {
      return null;
    }
    const remainingMs = new Date(incident.slaDueAt).getTime() - Date.now();
    const minutes = Math.round(Math.abs(remainingMs) / 60_000);
    const hours = Math.floor(minutes / 60);
    const remainder = minutes % 60;
    const duration = hours > 0 ? `${hours}h ${remainder}m` : `${remainder}m`;
    return remainingMs < 0 ? `Overdue by ${duration}` : `${duration} remaining`;
  }

  /** Elapsed fraction of the SLA window, for the progress bar — real, not fabricated: both
   * endpoints of the window (`createdAt`, `slaDueAt`) come straight from the incident. */
  slaProgressPct(incident: Incident): number {
    if (!incident.slaDueAt) {
      return 0;
    }
    const start = new Date(incident.createdAt).getTime();
    const due = new Date(incident.slaDueAt).getTime();
    const total = due - start;
    if (total <= 0) {
      return 100;
    }
    const elapsed = Date.now() - start;
    return Math.min(100, Math.max(0, Math.round((elapsed / total) * 100)));
  }

  priorityClass(incident: Incident): string {
    switch (incident.priority) {
      case 'HIGH':
        return 'controller-pill controller-pill--high';
      case 'MEDIUM':
        return 'controller-pill controller-pill--medium';
      default:
        return 'controller-pill controller-pill--low';
    }
  }

  private load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);

    const tab = this.activeTab();
    this.api
      .listIncidents({
        status: tab === 'New' ? 'NEW' : tab === 'In progress' ? 'IN_PROGRESS' : tab === 'Overdue' ? 'OVERDUE' : undefined,
        unassigned: tab === 'Unassigned' ? true : undefined,
        q: this.searchControl.value,
        page: 0,
        size: PAGE_SIZE,
      })
      .subscribe({
        next: (page) => {
          this.incidents.set(page.content);
          this.loading.set(false);
          if (!this.selectedId() && page.content.length > 0) {
            this.selectedId.set(page.content[0].id);
          }
        },
        error: () => {
          this.loading.set(false);
          this.loadFailed.set(true);
        },
      });
  }
}
