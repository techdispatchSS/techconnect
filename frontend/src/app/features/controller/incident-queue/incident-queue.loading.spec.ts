import { provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { IncidentKpis, PageResponse, Incident } from '../controller.models';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { NewIncidentNotifierService } from '../new-incident-notifier.service';
import { IncidentQueue } from './incident-queue';

const KPIS: IncidentKpis = { newCount: 1, unassignedCount: 1, inProgressCount: 0, overdueCount: 0 };

describe('IncidentQueue while loading', () => {
  it('shows placeholder rows and tiles instead of the word "Loading", then the data', async () => {
    const list = new Subject<PageResponse<Incident>>();
    const kpis = new Subject<IncidentKpis>();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: ControllerApiService,
          useValue: { listIncidents: () => list, kpis: () => kpis, getIncident: vi.fn() },
        },
        {
          provide: FreshdeskStatusService,
          useValue: { status: signal(null), refresh: vi.fn() },
        },
        { provide: NewIncidentNotifierService, useValue: { refresh: vi.fn() } },
      ],
    });
    const fixture = TestBed.createComponent(IncidentQueue);
    const el: HTMLElement = fixture.nativeElement;
    await fixture.whenStable();

    expect(el.querySelectorAll('.iq-table--loading tbody tr')).toHaveLength(8);
    expect(el.querySelectorAll('.iq-kpi app-skeleton').length).toBeGreaterThan(0);
    expect(el.textContent).not.toContain('Loading…');
    expect(el.querySelector('.iq-table--loading')!.getAttribute('aria-busy')).toBe('true');

    kpis.next(KPIS);
    list.next({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await fixture.whenStable();

    expect(el.querySelector('.iq-table--loading')).toBeNull();
    expect(el.textContent).toContain('No incidents in this view');
  });
});
