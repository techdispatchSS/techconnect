import { Component, input, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { DispatchSummary, Incident, TechnicianCandidate } from '../controller.models';
import { TechnicianMap } from '../technician-map/technician-map';
import { CreateDispatch } from './create-dispatch';

/** The real map needs Leaflet and a laid-out DOM; this page's behaviour doesn't depend on it. */
@Component({ selector: 'app-technician-map', template: '' })
class StubMap {
  readonly siteLatitude = input<number | null>(null);
  readonly siteLongitude = input<number | null>(null);
  readonly siteLabel = input('');
  readonly technicians = input<readonly TechnicianCandidate[]>([]);
}

function summary(over: Partial<DispatchSummary> = {}): DispatchSummary {
  return {
    id: 'd1',
    type: 'BROADCAST',
    status: 'PENDING',
    sentAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 600_000).toISOString(),
    invited: 2,
    pending: 2,
    declined: 0,
    noResponse: 0,
    acceptedTechnicianName: null,
    notificationCount: 1,
    lastNotifiedAt: new Date().toISOString(),
    ...over,
  };
}

const TECH: TechnicianCandidate = {
  id: 't1',
  name: 'Thabo M',
  score: 90,
  distanceKm: 3,
  latitude: null,
  longitude: null,
  matchedSkills: 0,
  totalRequiredSkills: 0,
  completedJobCount: 4,
  status: 'AVAILABLE',
};

describe('CreateDispatch', () => {
  let fixture: ComponentFixture<CreateDispatch>;
  let el: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;
  let createDispatch: ReturnType<typeof vi.fn>;

  async function setup(dispatch: DispatchSummary | null): Promise<void> {
    const incident = {
      id: 'inc-1',
      ticketRef: 'INC-0001',
      clientName: 'Acme',
      siteAddress: '1 Main Rd',
      siteLatitude: null,
      siteLongitude: null,
      requiredSkills: [],
      sla: '4 hrs',
      contactName: null,
      contactPhone: null,
      priority: 'HIGH',
      hasDispatch: dispatch !== null,
      dispatch,
    } as unknown as Incident;
    createDispatch = vi.fn(() =>
      of({ id: 'd2', incidentId: 'inc-1', dispatchType: 'BROADCAST', status: 'PENDING' }),
    );

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ incidentId: 'inc-1' }) } },
        },
        {
          provide: ControllerApiService,
          useValue: {
            getIncident: () => of(incident),
            recommendTechnicians: () => of([TECH]),
            createDispatch,
          },
        },
      ],
    });
    TestBed.overrideComponent(CreateDispatch, {
      remove: { imports: [TechnicianMap] },
      add: { imports: [StubMap] },
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(CreateDispatch);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  it('shows the form for an incident nothing has been sent for', async () => {
    await setup(null);

    expect(el.textContent).toContain('Broadcast job');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('sends you to the dispatch status instead when one is already out', async () => {
    await setup(summary());

    expect(navigate).toHaveBeenCalledWith(['/incidents/progress', 'inc-1'], { replaceUrl: true });
    expect(el.textContent).not.toContain('Broadcast job');
  });

  it('does the same once a technician has accepted', async () => {
    await setup(summary({ status: 'ACCEPTED', pending: 0, acceptedTechnicianName: 'Thabo M' }));

    expect(navigate).toHaveBeenCalledWith(['/incidents/progress', 'inc-1'], { replaceUrl: true });
  });

  it('shows the form again when the last dispatch lapsed, so it can be re-sent', async () => {
    await setup(summary({ status: 'EXPIRED', pending: 0, noResponse: 2 }));

    expect(navigate).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Broadcast job');
  });

  it('lands on the dispatch status after sending, replacing the form in history', async () => {
    await setup(null);

    (el.querySelector('.cd-table-card tbody tr') as HTMLElement).click();
    await fixture.whenStable();
    const send = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent!.includes('Broadcast job'),
    ) as HTMLButtonElement;
    send.click();
    await fixture.whenStable();

    expect(createDispatch).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/incidents/progress', 'inc-1'], { replaceUrl: true });
  });
});
