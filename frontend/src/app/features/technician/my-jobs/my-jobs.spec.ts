import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { JobSummary } from '../../controller/controller.models';
import { TechnicianApiService } from '../technician-api.service';
import { MyJobs } from './my-jobs';

export function jobSummary(overrides: Partial<JobSummary> = {}): JobSummary {
  return {
    id: 'j1',
    incidentId: 'i1',
    ticketRef: 'FD-1042',
    clientName: 'Acme Mining',
    siteAddress: '12 Main Rd, Sandton',
    priority: 'HIGH',
    slaDueAt: null,
    overdue: false,
    technicianId: 't1',
    technicianName: 'Thandi',
    status: 'EN_ROUTE',
    acceptedAt: '2026-10-25T08:00:00Z',
    arrivedAt: null,
    startedAt: null,
    completedAt: null,
    submittedAt: null,
    closedAt: null,
    ...overrides,
  };
}

describe('MyJobs', () => {
  let myJobs: ReturnType<typeof vi.fn>;

  function setup(): ComponentFixture<MyJobs> {
    TestBed.configureTestingModule({
      imports: [MyJobs],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: TechnicianApiService, useValue: { myJobs } },
      ],
    });
    const fixture = TestBed.createComponent(MyJobs);
    fixture.detectChanges();
    return fixture;
  }

  function el(fixture: ComponentFixture<MyJobs>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('lists the jobs in hand as links to each job, and the finished ones apart', () => {
    myJobs = vi.fn(() =>
      of([
        jobSummary(),
        jobSummary({ id: 'j2', clientName: 'Held Co', status: 'ON_HOLD' }),
        jobSummary({ id: 'j3', clientName: 'Done Co', status: 'SUBMITTED' }),
      ]),
    );
    const fixture = setup();

    const cards = el(fixture).querySelectorAll('a.mj-card');
    expect(cards.length).toBe(2);
    expect(cards[0].getAttribute('href')).toBe('/jobs/j1');
    expect(cards[0].textContent).toContain('En route');
    expect(cards[1].textContent).toContain('On hold');
    expect(el(fixture).querySelector('.mj-finished')?.textContent).toContain('Done Co');
  });

  it('says so when nothing is in hand', () => {
    myJobs = vi.fn(() => of([]));
    const fixture = setup();

    expect(el(fixture).textContent).toContain('Nothing in hand.');
  });

  it('offers a retry when the list cannot be loaded', () => {
    myJobs = vi.fn(() => throwError(() => new Error('offline')));
    const fixture = setup();
    expect(el(fixture).textContent).toContain("Couldn't load your jobs.");

    myJobs.mockReturnValue(of([jobSummary()]));
    (el(fixture).querySelector('.mj-retry') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(el(fixture).querySelectorAll('a.mj-card').length).toBe(1);
  });
});
