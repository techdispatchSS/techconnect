import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { JobCountsService } from '../job-counts.service';
import { JobStage, JobSummary, PageResponse } from '../controller.models';
import { JobList } from './job-list';

function job(overrides: Partial<JobSummary> = {}): JobSummary {
  return {
    id: 'j1',
    incidentId: 'i1',
    ticketRef: 'SIM-1001',
    clientName: 'Acme Logistics',
    siteAddress: '1 Main Rd, Sandton',
    priority: 'HIGH',
    slaDueAt: new Date(Date.now() + 3 * 3_600_000).toISOString(),
    overdue: false,
    technicianId: 't1',
    technicianName: 'Thandi Mokoena',
    status: 'ON_SITE',
    acceptedAt: new Date(Date.now() - 40 * 60_000).toISOString(),
    arrivedAt: null,
    startedAt: null,
    completedAt: null,
    submittedAt: null,
    closedAt: null,
    ...overrides,
  };
}

function page(rows: JobSummary[]): PageResponse<JobSummary> {
  return { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 };
}

describe('JobList', () => {
  let fixture: ComponentFixture<JobList>;
  let el: HTMLElement;
  let listJobs: ReturnType<typeof vi.fn>;

  async function setup(
    stages: JobStage[],
    rows: JobSummary[] = [job()],
    options: { fail?: boolean } = {},
  ): Promise<void> {
    // Only the interval is faked: Angular's own stability tracking needs the real setTimeout.
    // The debounce reads the clock as well as the interval, so the clock is faked with it.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    listJobs = vi.fn(() => (options.fail ? throwError(() => new Error('down')) : of(page(rows))));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { stages } } } },
        { provide: ControllerApiService, useValue: { listJobs } },
        {
          provide: JobCountsService,
          useValue: {
            counts: signal({ active: 4, review: 2, closed: 9 }),
            refresh: vi.fn(),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(JobList);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  it('lists the jobs in hand with who is on them and where they stand', async () => {
    await setup(['ACTIVE']);

    expect(listJobs).toHaveBeenCalledWith('ACTIVE', '', 0, 20);
    expect(el.textContent).toContain('SIM-1001');
    expect(el.textContent).toContain('Acme Logistics');
    expect(el.textContent).toContain('Thandi Mokoena');
    expect(el.textContent).toContain('On site');
    expect(el.textContent).toContain('left');
  });

  it('has no tabs when there is only one stage to show', async () => {
    await setup(['ACTIVE']);
    expect(el.querySelector('.jl-tabs')).toBeNull();
  });

  it('offers to review and closed, with counts, and switching asks for that stage', async () => {
    await setup(['REVIEW', 'CLOSED']);
    expect(listJobs).toHaveBeenLastCalledWith('REVIEW', '', 0, 20);

    const tabs = Array.from(el.querySelectorAll<HTMLButtonElement>('.jl-tab'));
    expect(tabs).toHaveLength(2);
    expect(tabs[0].textContent).toContain('To review');
    expect(tabs[0].textContent).toContain('2');
    expect(tabs[1].textContent).toContain('Closed');
    expect(tabs[1].textContent).toContain('9');

    tabs[1].click();
    await fixture.whenStable();
    expect(listJobs).toHaveBeenLastCalledWith('CLOSED', '', 0, 20);
  });

  it('says what an empty list means for the stage', async () => {
    await setup(['REVIEW', 'CLOSED'], []);
    expect(el.textContent).toContain('Nothing is waiting for review');
  });

  it('flags a job past its SLA', async () => {
    await setup(
      ['ACTIVE'],
      [job({ slaDueAt: new Date(Date.now() - 20 * 60_000).toISOString(), overdue: true })],
    );
    expect(el.textContent).toContain('Overdue by 20m');
    expect(el.querySelector('.jl-overdue')).not.toBeNull();
  });

  it('searches on the server, after the typing settles', async () => {
    await setup(['ACTIVE']);
    listJobs.mockClear();

    const input = el.querySelector<HTMLInputElement>('input')!;
    input.value = 'acme';
    input.dispatchEvent(new Event('input'));
    expect(listJobs).not.toHaveBeenCalled();

    vi.advanceTimersByTime(400);
    await fixture.whenStable();
    expect(listJobs).toHaveBeenCalledWith('ACTIVE', 'acme', 0, 20);
  });

  it('opens a job on its own page', async () => {
    await setup(['ACTIVE']);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    el.querySelector<HTMLButtonElement>('.jl-ticket-btn')!.click();
    expect(navigate).toHaveBeenCalledWith(['/incidents/jobs', 'j1']);
  });

  it('says it could not load, rather than showing an empty list', async () => {
    await setup(['ACTIVE'], [], { fail: true });
    expect(el.textContent).toContain("Couldn't load the jobs");
    expect(el.textContent).not.toContain('No jobs are being worked');
  });
});
