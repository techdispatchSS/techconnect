import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, interval, switchMap, tap } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { needsNewDispatch } from '../dispatch-state';
import { DispatchType, Incident, PRIORITY_LABELS, TechnicianCandidate } from '../controller.models';
import { TechnicianMap } from '../technician-map/technician-map';
import { Skeleton } from '../../../shared/skeleton/skeleton';

/** How often the hold on the incident is renewed; the server's hold lasts two minutes. */
const LOCK_RENEWAL_MS = 40_000;
/** How often a blocked screen looks again to see whether the other controller has left. */
const LOCK_RETRY_MS = 3_000;

function splitList(raw: string): string[] {
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

export type TechnicianSortKey = 'name' | 'score' | 'distance' | 'skills' | 'closed' | 'status';

interface SortOption {
  readonly key: TechnicianSortKey;
  readonly label: string;
  /** Which way round the column goes the first time it is chosen. */
  readonly firstDir: 'asc' | 'desc';
  /** Wording for the phone's sort menu, per direction. */
  readonly asc: string;
  readonly desc: string;
}

const SORT_OPTIONS: readonly SortOption[] = [
  { key: 'name', label: 'Technician', firstDir: 'asc', asc: 'Name, A to Z', desc: 'Name, Z to A' },
  {
    key: 'score',
    label: 'Score',
    firstDir: 'desc',
    asc: 'Score, lowest first',
    desc: 'Score, highest first',
  },
  {
    key: 'distance',
    label: 'Distance',
    firstDir: 'asc',
    asc: 'Distance, nearest first',
    desc: 'Distance, furthest first',
  },
  {
    key: 'skills',
    label: 'Skills',
    firstDir: 'desc',
    asc: 'Skills matched, fewest first',
    desc: 'Skills matched, most first',
  },
  {
    key: 'closed',
    label: 'Closed jobs',
    firstDir: 'desc',
    asc: 'Closed jobs, fewest first',
    desc: 'Closed jobs, most first',
  },
  {
    key: 'status',
    label: 'Status',
    firstDir: 'asc',
    asc: 'Available first',
    desc: 'Offline first',
  },
];

const STATUS_RANK: Record<TechnicianCandidate['status'], number> = {
  AVAILABLE: 0,
  ON_JOB: 1,
  OFFLINE: 2,
};

function compareBy(key: TechnicianSortKey, a: TechnicianCandidate, b: TechnicianCandidate): number {
  switch (key) {
    case 'name':
      return a.name.localeCompare(b.name);
    case 'score':
      return a.score - b.score;
    case 'distance':
      return (a.distanceKm ?? 0) - (b.distanceKm ?? 0);
    case 'skills':
      return a.matchedSkills - b.matchedSkills;
    case 'closed':
      return a.completedJobCount - b.completedJobCount;
    case 'status':
      return STATUS_RANK[a.status] - STATUS_RANK[b.status];
  }
}

@Component({
  selector: 'app-create-dispatch',
  imports: [ReactiveFormsModule, RouterLink, TechnicianMap, Skeleton],
  templateUrl: './create-dispatch.html',
  styleUrl: './create-dispatch.scss',
})
export class CreateDispatch implements OnInit, OnDestroy {
  private readonly api = inject(ControllerApiService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly priorityLabels = PRIORITY_LABELS;

  private readonly incidentId = this.route.snapshot.paramMap.get('incidentId')!;

  readonly incident = signal<Incident | null>(null);
  readonly loadFailed = signal(false);

  readonly technicians = signal<TechnicianCandidate[]>([]);
  readonly loadingTechnicians = signal(true);
  readonly skeletonRows = [1, 2, 3, 4, 5];
  readonly selectedIds = signal<ReadonlySet<string>>(new Set());
  readonly method = signal<DispatchType>('BROADCAST');
  readonly submitting = signal(false);
  readonly submitFailed = signal(false);
  readonly submitMessage = signal("Couldn't create the dispatch. Try again.");

  /**
   * Why this controller cannot dispatch it: someone else is already at it (the server names them).
   * The screen holds the incident while it is open, so two controllers do not both send it out.
   */
  readonly lockMessage = signal<string | null>(null);
  /** Whether the first attempt to take the incident has been answered, so the form is never shown
   * for a moment to someone who is about to be told it is taken. */
  readonly lockChecked = signal(false);
  private holdingLock = false;

  readonly form = this.fb.nonNullable.group({
    jobType: ['Incident'],
    requiredSkills: [''],
    requiredCertifications: [''],
    slaResponse: [''],
    siteContact: [''],
    notesForTechnician: [''],
  });

  // --- sorting ---------------------------------------------------------------------------
  // Until a column is chosen the table keeps the server's own ranking (skill match, then
  // availability, then distance), which is what "recommended" means.
  readonly sortKey = signal<TechnicianSortKey | null>(null);
  readonly sortDir = signal<'asc' | 'desc'>('desc');
  readonly sortOptions = SORT_OPTIONS;

  readonly sortedTechnicians = computed(() => {
    const key = this.sortKey();
    const rows = this.technicians();
    if (!key) {
      return rows;
    }
    const direction = this.sortDir() === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      // A distance nobody knows goes to the bottom whichever way round the column is sorted.
      if (key === 'distance') {
        if (a.distanceKm === null || b.distanceKm === null) {
          return a.distanceKm === b.distanceKm ? 0 : a.distanceKm === null ? 1 : -1;
        }
      }
      const result = compareBy(key, a, b) * direction;
      return result !== 0 ? result : b.score - a.score || a.name.localeCompare(b.name);
    });
  });

  /** Clicking the active column flips it; a new column starts in its natural direction. */
  sortBy(key: TechnicianSortKey): void {
    if (this.sortKey() === key) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
      return;
    }
    this.sortKey.set(key);
    this.sortDir.set(SORT_OPTIONS.find((o) => o.key === key)!.firstDir);
  }

  ariaSort(key: TechnicianSortKey): 'ascending' | 'descending' | 'none' {
    if (this.sortKey() !== key) {
      return 'none';
    }
    return this.sortDir() === 'asc' ? 'ascending' : 'descending';
  }

  sortIcon(key: TechnicianSortKey): string {
    if (this.sortKey() !== key) {
      return '↕';
    }
    return this.sortDir() === 'asc' ? '▲' : '▼';
  }

  /** The phone's sort menu: a single choice that already says which way round. */
  readonly sortChoice = computed(() => {
    const key = this.sortKey();
    return key ? `${key}:${this.sortDir()}` : '';
  });

  chooseSort(value: string): void {
    if (!value) {
      this.sortKey.set(null);
      return;
    }
    const [key, dir] = value.split(':') as [TechnicianSortKey, 'asc' | 'desc'];
    this.sortKey.set(key);
    this.sortDir.set(dir);
  }

  readonly selectedTechnicians = computed(() =>
    this.technicians().filter((t) => this.selectedIds().has(t.id)),
  );

  ngOnInit(): void {
    this.loadIncident();

    this.holdIncident();
    // Holding it: renew now and then. Blocked: look again every few seconds, so the screen opens
    // almost the moment the other controller leaves rather than at the next slow renewal.
    interval(LOCK_RENEWAL_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (!this.lockMessage()) {
          this.holdIncident();
        }
      });
    interval(LOCK_RETRY_MS)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        if (this.lockMessage()) {
          this.holdIncident();
        }
      });

    this.form.controls.requiredSkills.valueChanges
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => this.loadingTechnicians.set(true)),
        switchMap((raw) => this.api.recommendTechnicians(this.incidentId, splitList(raw))),
      )
      .subscribe((candidates) => {
        this.technicians.set(candidates);
        this.loadingTechnicians.set(false);
      });

    this.api
      .recommendTechnicians(this.incidentId, splitList(this.form.controls.requiredSkills.value))
      .subscribe((candidates) => {
        this.technicians.set(candidates);
        this.loadingTechnicians.set(false);
      });
  }

  /** Reads the incident; sends the controller to its progress if a dispatch is already out. */
  private loadIncident(): void {
    this.api.getIncident(this.incidentId).subscribe({
      next: (incident) => {
        // Already sent (and still out, or taken): there is nothing to create here. Show where
        // it stands instead of a form whose send button would only be refused. `replaceUrl` so
        // the browser's Back button doesn't land on this form again.
        if (!needsNewDispatch(incident.dispatch)) {
          void this.router.navigate(['/incidents/progress', incident.id], { replaceUrl: true });
          return;
        }
        this.incident.set(incident);
        this.form.patchValue({
          requiredSkills: incident.requiredSkills.join(', '),
          slaResponse: incident.sla ?? '',
          siteContact: incident.contactName
            ? `${incident.contactName}${incident.contactPhone ? ' · ' + incident.contactPhone : ''}`
            : '',
        });
      },
      error: () => this.loadFailed.set(true),
    });
  }

  /**
   * Takes the hold on the incident, and renews it. A refusal means someone else has it (or took it
   * after this one lapsed), and the form stops offering to send.
   */
  private holdIncident(): void {
    this.api.lockIncident(this.incidentId).subscribe({
      next: () => {
        const wasBlocked = this.lockMessage() !== null;
        this.holdingLock = true;
        this.lockMessage.set(null);
        this.lockChecked.set(true);
        // It was taken and is free now. What they did meanwhile may have changed it (they may
        // have sent it), so read it again, which also moves on if a dispatch is out.
        if (wasBlocked) {
          this.loadIncident();
        }
      },
      error: (error: unknown) => {
        this.lockChecked.set(true);
        if (error instanceof HttpErrorResponse && error.status === 409) {
          this.holdingLock = false;
          // Still blocked: if they sent it, there is nothing left to dispatch, so go to where it
          // stands instead of waiting here.
          if (this.lockMessage() !== null) {
            this.loadIncident();
          }
          this.lockMessage.set(
            (error.error as { error?: string } | null)?.error ??
              'Someone else is dispatching this incident.',
          );
        }
        // Any other failure (a dropped connection) is left to the next renewal; the server still
        // refuses a second live dispatch whatever happens here.
      },
    });
  }

  /** Leaving the screen lets the incident go, rather than holding it until it lapses. */
  ngOnDestroy(): void {
    if (this.holdingLock) {
      this.api.unlockIncident(this.incidentId).subscribe({ error: () => undefined });
    }
  }

  pickMethod(method: DispatchType): void {
    this.method.set(method);
    if (method === 'ASSIGN' && this.selectedIds().size > 1) {
      const first = [...this.selectedIds()][0];
      this.selectedIds.set(new Set(first ? [first] : []));
    }
  }

  toggleTechnician(id: string): void {
    const current = new Set(this.selectedIds());
    if (current.has(id)) {
      current.delete(id);
      this.selectedIds.set(current);
      return;
    }
    if (this.method() === 'ASSIGN') {
      this.selectedIds.set(new Set([id]));
    } else {
      current.add(id);
      this.selectedIds.set(current);
    }
  }

  removeTechnician(id: string): void {
    const current = new Set(this.selectedIds());
    current.delete(id);
    this.selectedIds.set(current);
  }

  scoreBarStyle(score: number): Record<string, string> {
    return { width: `${score}%` };
  }

  submit(): void {
    const incident = this.incident();
    if (!incident || this.selectedIds().size === 0 || this.submitting() || this.lockMessage()) {
      return;
    }

    this.submitting.set(true);
    this.submitFailed.set(false);

    const value = this.form.getRawValue();
    this.api
      .createDispatch({
        incidentId: incident.id,
        dispatchType: this.method(),
        jobType: value.jobType || null,
        requiredSkills: splitList(value.requiredSkills),
        requiredCertifications: splitList(value.requiredCertifications),
        slaResponse: value.slaResponse || null,
        siteContact: value.siteContact || null,
        notesForTechnician: value.notesForTechnician || null,
        technicianIds: [...this.selectedIds()],
      })
      .subscribe({
        // Land on the ticket's progress, which now says who it was sent to and that it is
        // awaiting confirmation — rather than back in a list where that state is easy to miss.
        next: (dispatch) =>
          this.router.navigate(['/incidents/progress', dispatch.incidentId], { replaceUrl: true }),
        error: (error: unknown) => {
          this.submitting.set(false);
          this.submitFailed.set(true);
          // The server says why in plain words for the cases a Controller can act on — someone
          // else already dispatched this, a technician was deactivated meanwhile.
          const server =
            error instanceof HttpErrorResponse && [400, 409].includes(error.status)
              ? (error.error as { error?: string } | null)?.error
              : null;
          this.submitMessage.set(
            server && server !== 'Validation failed'
              ? server
              : "Couldn't create the dispatch. Try again.",
          );
        },
      });
  }
}
