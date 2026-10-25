import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import {
  DispatchLifecycleStatus,
  DispatchResponseEntry,
  FreshdeskPushMode,
  FreshdeskStatus,
  FreshdeskUpdate,
  Incident,
  IncidentProgress,
} from '../controller.models';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { TicketProgress } from './ticket-progress';

const NOW = new Date().toISOString();

function entry(id: string, response: DispatchResponseEntry['response']): DispatchResponseEntry {
  return {
    technicianId: id,
    technicianName: `Tech ${id}`,
    response,
    respondedAt: response === 'PENDING' ? null : NOW,
  };
}

function progress(
  status: DispatchLifecycleStatus,
  responses: DispatchResponseEntry[],
): IncidentProgress {
  return {
    incident: {
      id: 'i1',
      ticketRef: 'INC-0001',
      clientName: 'Acme',
      siteAddress: '1 Main Rd',
      priority: 'HIGH',
      sla: '4 hrs',
      dispatch: {
        id: 'd1',
        type: 'BROADCAST',
        status,
        sentAt: NOW,
        expiresAt: new Date(Date.now() + 600_000).toISOString(),
        invited: responses.length,
        pending: responses.filter((r) => r.response === 'PENDING').length,
        declined: responses.filter((r) => r.response === 'DECLINED').length,
        noResponse: responses.filter((r) => r.response === 'NO_RESPONSE').length,
        acceptedTechnicianName: null,
        notificationCount: 1,
        lastNotifiedAt: NOW,
      },
    } as Incident,
    dispatchId: 'd1',
    dispatchType: 'BROADCAST',
    dispatchStatus: status,
    jobType: 'Incident',
    requiredSkills: [],
    requiredCertifications: [],
    slaResponse: null,
    siteContact: null,
    notesForTechnician: null,
    dispatchCreatedAt: NOW,
    dispatchExpiresAt: new Date(Date.now() + 600_000).toISOString(),
    responses,
    acceptedTechnicianName:
      responses.find((r) => r.response === 'ACCEPTED')?.technicianName ?? null,
    jobId: status === 'ACCEPTED' ? 'j1' : null,
    jobStatus: status === 'ACCEPTED' ? 'EN_ROUTE' : null,
  };
}

const SIMULATED: FreshdeskStatus = {
  mode: 'SIMULATED',
  simulation: true,
  lastSyncAt: null,
  lastSuccessAt: null,
  lastError: null,
  pushMode: 'DEMO',
};

function update(
  id: number,
  action: FreshdeskUpdate['action'],
  body: string | null,
  delivery: FreshdeskUpdate['delivery'],
): FreshdeskUpdate {
  return {
    id,
    event: action === 'RESOLVE' ? 'JOB_CLOSED' : 'JOB_PROGRESS',
    action,
    body,
    delivery,
    lastError: null,
    createdAt: '2026-10-25T09:30:00Z',
    sentAt: delivery === 'SENT' ? '2026-10-25T09:30:05Z' : null,
  };
}

describe('TicketProgress', () => {
  let fixture: ComponentFixture<TicketProgress>;
  let el: HTMLElement;
  let getIncidentProgress: ReturnType<typeof vi.fn>;
  let simulateTechnicianResponse: ReturnType<typeof vi.fn>;
  let resendDispatch: ReturnType<typeof vi.fn>;
  let getFreshdeskUpdates: ReturnType<typeof vi.fn>;

  async function setup(
    first: IncidentProgress,
    options: {
      simulation?: boolean;
      updates?: FreshdeskUpdate[];
      pushMode?: FreshdeskPushMode;
    } = {},
  ): Promise<void> {
    // Only the interval is faked: Angular's own stability tracking needs the real setTimeout.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    getIncidentProgress = vi.fn(() => of(first));
    getFreshdeskUpdates = vi.fn(() => of(options.updates ?? []));
    resendDispatch = vi.fn(() =>
      of({ notified: 1, notificationCount: 2, lastNotifiedAt: new Date().toISOString() }),
    );
    simulateTechnicianResponse = vi.fn(() =>
      of({ dispatchId: 'd1', outcome: 'ACCEPTED' as const }),
    );

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ incidentId: 'i1' }) } },
        },
        {
          provide: ControllerApiService,
          useValue: {
            getIncidentProgress,
            getFreshdeskUpdates,
            simulateTechnicianResponse,
            resendDispatch,
          },
        },
        {
          provide: FreshdeskStatusService,
          useValue: {
            status: signal(
              options.simulation || options.pushMode
                ? {
                    ...SIMULATED,
                    simulation: !!options.simulation,
                    pushMode: options.pushMode ?? 'DEMO',
                  }
                : null,
            ),
          },
        },
      ],
    });

    fixture = TestBed.createComponent(TicketProgress);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  it('keeps re-reading a dispatch nobody has answered yet', async () => {
    await setup(progress('PENDING', [entry('t1', 'PENDING')]));
    expect(getIncidentProgress).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(10_000);
    await fixture.whenStable();
    expect(getIncidentProgress).toHaveBeenCalledTimes(2);

    vi.advanceTimersByTime(10_000);
    await fixture.whenStable();
    expect(getIncidentProgress).toHaveBeenCalledTimes(3);
  });

  it('stops asking once the dispatch has been decided', async () => {
    await setup(progress('ACCEPTED', [entry('t1', 'ACCEPTED')]));
    expect(getIncidentProgress).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(60_000);
    await fixture.whenStable();

    expect(getIncidentProgress).toHaveBeenCalledTimes(1);
    expect(el.textContent).toContain('Assigned to Tech t1');
  });

  it('says plainly when every technician declined, rather than blaming the clock', async () => {
    await setup(progress('EXPIRED', [entry('t1', 'DECLINED'), entry('t2', 'DECLINED')]));

    expect(el.textContent).toContain('Every technician declined');
    expect(el.textContent).not.toContain('No technician accepted in time');
  });

  it('keeps the original wording when the window simply ran out', async () => {
    await setup(progress('EXPIRED', [entry('t1', 'NO_RESPONSE'), entry('t2', 'DECLINED')]));

    expect(el.textContent).toContain('No technician accepted in time');
  });

  it('has no answer controls outside simulation', async () => {
    await setup(progress('PENDING', [entry('t1', 'PENDING')]), { simulation: false });

    expect(el.querySelector('.tp-sim-actions')).toBeNull();
  });

  it('lets the demo answer as a technician who has not answered, and reloads', async () => {
    await setup(progress('PENDING', [entry('t1', 'PENDING'), entry('t2', 'DECLINED')]), {
      simulation: true,
    });

    // Only the technician who hasn't answered gets the buttons.
    expect(el.querySelectorAll('.tp-sim-actions')).toHaveLength(1);
    const accept = Array.from(el.querySelectorAll('.tp-sim-btn')).find(
      (b) => b.textContent!.trim() === 'Accept',
    ) as HTMLButtonElement;
    accept.click();
    await fixture.whenStable();

    expect(simulateTechnicianResponse).toHaveBeenCalledWith('d1', 't1', 'ACCEPTED');
    expect(getIncidentProgress).toHaveBeenCalledTimes(2);
  });

  it('says it was sent to the technicians and is awaiting their confirmation', async () => {
    await setup(progress('PENDING', [entry('t1', 'PENDING'), entry('t2', 'DECLINED')]));

    const banner = el.querySelector('.tp-banner')!.textContent!;
    expect(banner).toContain('Sent to 2 technicians — awaiting confirmation');
    expect(banner).toContain('1 declined · 1 yet to answer');
    expect(banner).toContain('Technicians notified once');
  });

  it('resends the notification and reloads', async () => {
    await setup(progress('PENDING', [entry('t1', 'PENDING')]));
    const callsBefore = getIncidentProgress.mock.calls.length;

    const resend = Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent!.includes('Resend notification'),
    ) as HTMLButtonElement;
    resend.click();
    await fixture.whenStable();

    expect(resendDispatch).toHaveBeenCalledWith('d1');
    expect(el.textContent).toContain('sent again to 1 technician.');
    expect(getIncidentProgress.mock.calls.length).toBeGreaterThan(callsBefore);
  });

  it('offers no resend once the job has been taken', async () => {
    await setup(progress('ACCEPTED', [entry('t1', 'ACCEPTED')]));

    expect(el.textContent).not.toContain('Resend notification');
  });

  it('lists the Freshdesk ticket updates and marks demo ones as not sent', async () => {
    await setup(progress('ACCEPTED', [entry('t1', 'ACCEPTED')]), {
      updates: [
        update(1, 'NOTE', 'Tech t1 accepted the job and is on the way to site.', 'DEMO'),
        update(2, 'RESOLVE', null, 'DEMO'),
      ],
    });

    const items = Array.from(el.querySelectorAll('.tp-fd-item')).map((i) => i.textContent!);
    expect(getFreshdeskUpdates).toHaveBeenCalledWith('i1');
    expect(items).toHaveLength(2);
    expect(items[0]).toContain('Tech t1 accepted the job and is on the way to site.');
    expect(items[0]).toContain('Demo · not sent');
    expect(items[1]).toContain('Ticket set to Resolved');
    expect(el.textContent).toContain('Nothing has been sent.');
  });

  it('drops the demo wording once updates really go to Freshdesk', async () => {
    await setup(progress('ACCEPTED', [entry('t1', 'ACCEPTED')]), {
      pushMode: 'LIVE',
      updates: [update(1, 'NOTE', 'Arrived on site.', 'SENT')],
    });

    expect(el.querySelector('.tp-fd-item')!.textContent).toContain('Sent');
    expect(el.textContent).not.toContain('Nothing has been sent.');
  });

  it('has no Freshdesk section for an incident without a ticket', async () => {
    await setup(progress('ACCEPTED', [entry('t1', 'ACCEPTED')]));

    expect(el.textContent).not.toContain('Freshdesk ticket updates');
  });
});
