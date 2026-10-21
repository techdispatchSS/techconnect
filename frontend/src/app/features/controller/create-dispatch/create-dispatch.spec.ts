import { Component, input, provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';

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

/** What the server says when another controller already has the incident open. */
function lockRefusal(name: string): HttpErrorResponse {
  return new HttpErrorResponse({
    status: 409,
    error: { error: `${name} is dispatching this incident right now. Try again in a moment.` },
  });
}

describe('CreateDispatch', () => {
  let fixture: ComponentFixture<CreateDispatch>;
  let el: HTMLElement;
  let navigate: ReturnType<typeof vi.spyOn>;
  let createDispatch: ReturnType<typeof vi.fn>;
  let lockIncident: ReturnType<typeof vi.fn>;
  let unlockIncident: ReturnType<typeof vi.fn>;

  async function setup(
    dispatch: DispatchSummary | null,
    technicians: TechnicianCandidate[] = [TECH],
    options: { lockedBy?: string } = {},
  ): Promise<void> {
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
    lockIncident = vi.fn(() =>
      options.lockedBy ? throwError(() => lockRefusal(options.lockedBy!)) : of({}),
    );
    unlockIncident = vi.fn(() => of(undefined));

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
            recommendTechnicians: () => of(technicians),
            createDispatch,
            lockIncident,
            unlockIncident,
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

  describe('holding the incident while dispatching it', () => {
    it('takes the incident when the screen opens, and lets go when it is left', async () => {
      await setup(null);
      expect(lockIncident).toHaveBeenCalledWith('inc-1');

      fixture.destroy();
      expect(unlockIncident).toHaveBeenCalledWith('inc-1');
    });

    it('keeps renewing the hold while the screen stays open', async () => {
      vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
      try {
        await setup(null);
        expect(lockIncident).toHaveBeenCalledTimes(1);

        vi.advanceTimersByTime(40_000);
        expect(lockIncident).toHaveBeenCalledTimes(2);
        vi.advanceTimersByTime(40_000);
        expect(lockIncident).toHaveBeenCalledTimes(3);
      } finally {
        vi.useRealTimers();
      }
    });

    it('says who has it, and will not send, when another controller is dispatching it', async () => {
      await setup(null, [TECH], { lockedBy: 'Sarah Dlamini' });

      expect(el.querySelector('.cd-lock')!.textContent).toContain('Sarah Dlamini is dispatching');

      (el.querySelector('.cd-table-card tbody tr') as HTMLElement).click();
      await fixture.whenStable();
      const send = Array.from(el.querySelectorAll('button')).find((b) =>
        b.textContent!.includes('Broadcast job'),
      ) as HTMLButtonElement;
      expect(send.disabled).toBe(true);
      send.click();
      await fixture.whenStable();
      expect(createDispatch).not.toHaveBeenCalled();
    });

    it('does not try to let go of a hold it never had', async () => {
      await setup(null, [TECH], { lockedBy: 'Sarah Dlamini' });
      fixture.destroy();
      expect(unlockIncident).not.toHaveBeenCalled();
    });

    it('says nothing when it has the incident to itself', async () => {
      await setup(null);
      expect(el.querySelector('.cd-lock')).toBeNull();
    });
  });

  describe('sorting the recommended technicians', () => {
    const THABO = {
      ...TECH,
      id: 't1',
      name: 'Thabo M',
      score: 90,
      distanceKm: 3,
      completedJobCount: 4,
    };
    const ZANELE = {
      ...TECH,
      id: 't2',
      name: 'Zanele K',
      score: 70,
      distanceKm: 12,
      completedJobCount: 20,
      status: 'OFFLINE' as const,
    };
    const AMAN = {
      ...TECH,
      id: 't3',
      name: 'Aman P',
      score: 80,
      distanceKm: null,
      completedJobCount: 9,
      status: 'ON_JOB' as const,
    };

    const names = (): string[] =>
      [...el.querySelectorAll('.cd-table-card tbody .cd-tech-name')].map((c) =>
        c.textContent!.trim(),
      );

    async function click(label: string): Promise<void> {
      const button = [...el.querySelectorAll<HTMLButtonElement>('.cd-sort')].find((b) =>
        b.textContent!.includes(label),
      )!;
      button.click();
      await fixture.whenStable();
    }

    it('keeps the recommended order until a column is chosen', async () => {
      await setup(null, [ZANELE, THABO, AMAN]);

      expect(names()).toEqual(['Zanele K', 'Thabo M', 'Aman P']);
    });

    it('sorts by a column, and flips it when the same column is clicked again', async () => {
      await setup(null, [ZANELE, THABO, AMAN]);

      await click('Score');
      expect(names()).toEqual(['Thabo M', 'Aman P', 'Zanele K']);
      expect(el.querySelector('th[aria-sort="descending"]')).not.toBeNull();

      await click('Score');
      expect(names()).toEqual(['Zanele K', 'Aman P', 'Thabo M']);
      expect(el.querySelector('th[aria-sort="ascending"]')).not.toBeNull();
    });

    it('starts names A to Z and distances nearest first, with unknown distances last', async () => {
      await setup(null, [ZANELE, THABO, AMAN]);

      await click('Technician');
      expect(names()).toEqual(['Aman P', 'Thabo M', 'Zanele K']);

      await click('Distance');
      expect(names()).toEqual(['Thabo M', 'Zanele K', 'Aman P']);

      // Reversed, the unknown distance still goes to the bottom.
      await click('Distance');
      expect(names()).toEqual(['Zanele K', 'Thabo M', 'Aman P']);
    });

    it('puts available technicians first when sorting by status', async () => {
      await setup(null, [ZANELE, THABO, AMAN]);

      await click('Status');

      expect(names()).toEqual(['Thabo M', 'Aman P', 'Zanele K']);
    });

    it('keeps the selection when the order changes', async () => {
      await setup(null, [ZANELE, THABO, AMAN]);
      (el.querySelectorAll('.cd-table-card tbody tr')[1] as HTMLElement).click();
      await fixture.whenStable();

      await click('Technician');

      const selected = el.querySelector('.cd-row--selected .cd-tech-name')!;
      expect(selected.textContent!.trim()).toBe('Thabo M');
    });

    it('offers the same choices as a menu for phones, and can return to the default', async () => {
      await setup(null, [ZANELE, THABO, AMAN]);
      const menu = el.querySelector<HTMLSelectElement>('#cd-sort-choice')!;

      menu.value = 'closed:desc';
      menu.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(names()).toEqual(['Zanele K', 'Aman P', 'Thabo M']);

      menu.value = '';
      menu.dispatchEvent(new Event('change'));
      await fixture.whenStable();
      expect(names()).toEqual(['Zanele K', 'Thabo M', 'Aman P']);
    });
  });
});
