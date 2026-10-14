import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { FreshdeskStatus, JobDetail, JobStatus } from '../controller.models';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { JobCountsService } from '../job-counts.service';
import { JobDetailPage } from './job-detail';

const SIMULATED: FreshdeskStatus = {
  mode: 'SIMULATED',
  simulation: true,
  lastSyncAt: null,
  lastSuccessAt: null,
  lastError: null,
};

function detail(status: JobStatus, overrides: Partial<JobDetail> = {}): JobDetail {
  const at = new Date().toISOString();
  const reported = status === 'SUBMITTED' || status === 'CLOSED';
  return {
    job: {
      id: 'j1',
      incidentId: 'i1',
      ticketRef: 'SIM-1001',
      clientName: 'Acme Logistics',
      siteAddress: '1 Main Rd',
      priority: 'HIGH',
      slaDueAt: new Date(Date.now() + 3_600_000).toISOString(),
      overdue: false,
      technicianId: 't1',
      technicianName: 'Thandi Mokoena',
      status,
      acceptedAt: at,
      arrivedAt: null,
      startedAt: null,
      completedAt: null,
      submittedAt: reported ? at : null,
      closedAt: status === 'CLOSED' ? at : null,
    },
    contactName: 'Pat',
    contactPhone: '011 555 0100',
    issueType: 'CCTV',
    description: 'Cameras are offline.',
    jobType: 'Incident',
    notesForTechnician: 'Ask for the comms room key.',
    resolutionNotes: reported ? 'Replaced the PoE switch.' : null,
    rootCause: reported ? 'Failed switch' : null,
    recommendations: null,
    labourHours: reported ? 2.5 : null,
    parts: reported ? [{ name: '24-port PoE switch', quantity: 1, serialNumber: 'SN-1' }] : [],
    closingNotes: status === 'CLOSED' ? 'Verified.' : null,
    closedByName: status === 'CLOSED' ? 'Corrie Controller' : null,
    history: [
      {
        status: 'EN_ROUTE',
        previousStatus: null,
        reason: 'Dispatch accepted',
        changedById: 't1',
        changedByName: 'Thandi Mokoena',
        changedAt: at,
      },
    ],
    canClose: status === 'SUBMITTED',
    ...overrides,
  };
}

describe('JobDetailPage', () => {
  let fixture: ComponentFixture<JobDetailPage>;
  let el: HTMLElement;
  let getJob: ReturnType<typeof vi.fn>;
  let closeJob: ReturnType<typeof vi.fn>;
  let simulateJobStatus: ReturnType<typeof vi.fn>;
  let simulateJobReport: ReturnType<typeof vi.fn>;

  async function setup(
    first: JobDetail,
    options: { simulation?: boolean; closeFails?: boolean } = {},
  ): Promise<void> {
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval'] });
    getJob = vi.fn(() => of(first));
    closeJob = vi.fn(() =>
      options.closeFails
        ? throwError(
            () =>
              new HttpErrorResponse({
                status: 409,
                error: { error: 'This job is already closed.' },
              }),
          )
        : of(detail('CLOSED')),
    );
    simulateJobStatus = vi.fn((_id: string, status: JobStatus) => of(detail(status)));
    simulateJobReport = vi.fn(() => of(detail('SUBMITTED')));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ jobId: 'j1' }) } },
        },
        {
          provide: ControllerApiService,
          useValue: { getJob, closeJob, simulateJobStatus, simulateJobReport },
        },
        {
          provide: FreshdeskStatusService,
          useValue: { status: signal(options.simulation ? SIMULATED : null) },
        },
        { provide: JobCountsService, useValue: { refresh: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(JobDetailPage);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  const buttonNamed = (text: string): HTMLButtonElement | undefined =>
    Array.from(el.querySelectorAll<HTMLButtonElement>('button')).find((b) =>
      b.textContent!.includes(text),
    );

  it('shows where the job is, who is on it, and the timeline', async () => {
    await setup(detail('ON_SITE'));

    expect(el.textContent).toContain('SIM-1001');
    expect(el.textContent).toContain('Thandi Mokoena');
    expect(el.textContent).toContain('On site');
    expect(el.textContent).toContain('Dispatch accepted');
    expect(el.textContent).toContain('Ask for the comms room key.');
  });

  it('offers no way to close a job that has not been reported on', async () => {
    await setup(detail('WORK_STARTED'));
    expect(buttonNamed('Close job')).toBeUndefined();
    expect(el.textContent).not.toContain("Technician's report");
  });

  it('shows the report and closes a submitted job with the notes typed', async () => {
    await setup(detail('SUBMITTED'));

    expect(el.textContent).toContain('Replaced the PoE switch.');
    expect(el.textContent).toContain('24-port PoE switch');
    expect(el.textContent).toContain('2.5 hours');

    const notes = el.querySelector<HTMLTextAreaElement>('textarea')!;
    notes.value = '  Verified with the client.  ';
    notes.dispatchEvent(new Event('input'));
    buttonNamed('Close job')!.click();
    await fixture.whenStable();

    expect(closeJob).toHaveBeenCalledWith('j1', 'Verified with the client.');
    expect(el.textContent).toContain('Closed by Corrie Controller');
    expect(buttonNamed('Close job')).toBeUndefined();
  });

  it('sends no notes at all when none were typed', async () => {
    await setup(detail('SUBMITTED'));
    buttonNamed('Close job')!.click();
    await fixture.whenStable();
    expect(closeJob).toHaveBeenCalledWith('j1', null);
  });

  it('says why a close was refused, and keeps the job as it is', async () => {
    await setup(detail('SUBMITTED'), { closeFails: true });
    buttonNamed('Close job')!.click();
    await fixture.whenStable();

    expect(el.textContent).toContain('This job is already closed.');
  });

  it('keeps re-reading a job in hand, and stops once it is closed', async () => {
    await setup(detail('ON_SITE'));
    expect(getJob).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(10_000);
    await fixture.whenStable();
    expect(getJob).toHaveBeenCalledTimes(2);

    getJob.mockClear();
    TestBed.resetTestingModule();
    await setup(detail('CLOSED'));
    getJob.mockClear();
    vi.advanceTimersByTime(60_000);
    await fixture.whenStable();
    expect(getJob).not.toHaveBeenCalled();
  });

  describe('demo controls', () => {
    it('are only there when simulation is on', async () => {
      await setup(detail('ON_SITE'));
      expect(el.querySelector('.jd-sim')).toBeNull();
    });

    it('take the technician to their next step', async () => {
      await setup(detail('EN_ROUTE'), { simulation: true });

      buttonNamed('Arrive on site')!.click();
      await fixture.whenStable();

      expect(simulateJobStatus).toHaveBeenCalledWith('j1', 'ON_SITE', null);
      expect(el.textContent).toContain('On site');
    });

    it('submit the report once the work is complete', async () => {
      await setup(detail('COMPLETE'), { simulation: true });

      buttonNamed('Submit report')!.click();
      await fixture.whenStable();

      expect(simulateJobReport).toHaveBeenCalledWith('j1');
      expect(el.textContent).toContain('Replaced the PoE switch.');
    });

    it('put a job on hold with a reason the controller will see', async () => {
      await setup(detail('WORK_STARTED'), { simulation: true });

      const reason = el.querySelector<HTMLInputElement>('.jd-sim-reason')!;
      reason.value = 'Client has not opened the comms room';
      reason.dispatchEvent(new Event('input'));
      buttonNamed('Put on hold')!.click();
      await fixture.whenStable();

      expect(simulateJobStatus).toHaveBeenCalledWith(
        'j1',
        'ON_HOLD',
        'Client has not opened the comms room',
      );
    });

    it('go away once the report is in: the controller takes over', async () => {
      await setup(detail('SUBMITTED'), { simulation: true });
      expect(el.querySelector('.jd-sim')).toBeNull();
    });
  });
});
