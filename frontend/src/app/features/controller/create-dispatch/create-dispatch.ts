import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, switchMap, tap } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { DispatchType, Incident, PRIORITY_LABELS, TechnicianCandidate } from '../controller.models';
import { TechnicianMap } from '../technician-map/technician-map';

function splitList(raw: string): string[] {
  return raw
    .split(',')
    .map((s) => s.trim())
    .filter((s) => s.length > 0);
}

@Component({
  selector: 'app-create-dispatch',
  imports: [ReactiveFormsModule, RouterLink, TechnicianMap],
  templateUrl: './create-dispatch.html',
  styleUrl: './create-dispatch.scss',
})
export class CreateDispatch implements OnInit {
  private readonly api = inject(ControllerApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);

  readonly priorityLabels = PRIORITY_LABELS;

  private readonly incidentId = this.route.snapshot.paramMap.get('incidentId')!;

  readonly incident = signal<Incident | null>(null);
  readonly loadFailed = signal(false);

  readonly technicians = signal<TechnicianCandidate[]>([]);
  readonly loadingTechnicians = signal(true);
  readonly selectedIds = signal<ReadonlySet<string>>(new Set());
  readonly method = signal<DispatchType>('BROADCAST');
  readonly submitting = signal(false);
  readonly submitFailed = signal(false);

  readonly form = this.fb.nonNullable.group({
    jobType: ['Incident'],
    requiredSkills: [''],
    requiredCertifications: [''],
    slaResponse: [''],
    siteContact: [''],
    notesForTechnician: [''],
  });

  readonly selectedTechnicians = computed(() =>
    this.technicians().filter((t) => this.selectedIds().has(t.id)),
  );

  ngOnInit(): void {
    this.api.getIncident(this.incidentId).subscribe({
      next: (incident) => {
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
    if (!incident || this.selectedIds().size === 0 || this.submitting()) {
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
        next: () => this.router.navigate(['/incidents/queue']),
        error: () => {
          this.submitting.set(false);
          this.submitFailed.set(true);
        },
      });
  }
}
