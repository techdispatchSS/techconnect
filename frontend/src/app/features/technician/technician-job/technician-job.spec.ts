import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { JobDetail, JobStatus } from '../../controller/controller.models';
import { TechnicianApiService } from '../technician-api.service';
import { MyJob } from '../technician.models';
import { TechnicianJob } from './technician-job';

function myJob(status: JobStatus, nextSteps: JobStatus[]): MyJob {
  const detail: JobDetail = {
    job: {
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
      status,
      acceptedAt: '2026-10-25T08:00:00Z',
      arrivedAt: null,
      startedAt: null,
      completedAt: null,
      submittedAt: null,
      closedAt: null,
    },
    contactName: 'Sipho',
    contactPhone: '+27 82 000 0000',
    issueType: 'CCTV',
    description: 'Cameras on the east fence are down.',
    jobType: null,
    notesForTechnician: 'Ask for the gate code at reception.',
    resolutionNotes: null,
    rootCause: null,
    recommendations: null,
    labourHours: null,
    parts: [],
    closingNotes: null,
    closedByName: null,
    history: [
      {
        status: 'EN_ROUTE',
        previousStatus: null,
        reason: 'Dispatch accepted',
        changedById: 't1',
        changedByName: 'Thandi',
        changedAt: '2026-10-25T08:00:00Z',
      },
    ],
    canClose: false,
  };
  return { detail, nextSteps };
}

describe('TechnicianJob', () => {
  let api: { myJob: ReturnType<typeof vi.fn>; updateJobStatus: ReturnType<typeof vi.fn> };

  function setup(start: Observable<MyJob>): ComponentFixture<TechnicianJob> {
    api.myJob.mockReturnValue(start);
    TestBed.configureTestingModule({
      imports: [TechnicianJob],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: TechnicianApiService, useValue: api },
      ],
    });
    const fixture = TestBed.createComponent(TechnicianJob);
    fixture.componentRef.setInput('id', 'j1');
    fixture.detectChanges();
    return fixture;
  }

  function el(fixture: ComponentFixture<TechnicianJob>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function buttons(fixture: ComponentFixture<TechnicianJob>): string[] {
    return [...el(fixture).querySelectorAll('.tj-actions button')].map(
      (b) => b.textContent?.trim() ?? '',
    );
  }

  function click(fixture: ComponentFixture<TechnicianJob>, label: string): void {
    const button = [...el(fixture).querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === label,
    );
    button!.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = { myJob: vi.fn(), updateJobStatus: vi.fn() };
  });

  it('shows who to see, how to get there, and the history', () => {
    const fixture = setup(of(myJob('EN_ROUTE', ['ON_SITE'])));
    const text = el(fixture).textContent ?? '';

    expect(api.myJob).toHaveBeenCalledWith('j1');
    expect(text).toContain('Acme Mining');
    expect(text).toContain('Sipho');
    expect(el(fixture).querySelector('a[href="tel:+27 82 000 0000"]')).not.toBeNull();
    expect(el(fixture).querySelector('a[href*="google.com/maps"]')).not.toBeNull();
    expect(text).toContain('Ask for the gate code at reception.');
    expect(text).toContain('Dispatch accepted');
  });

  it('moves the job on with one tap and shows the next steps the server allows', () => {
    const fixture = setup(of(myJob('EN_ROUTE', ['ON_SITE'])));
    expect(buttons(fixture)).toEqual(["I've arrived"]);
    api.updateJobStatus.mockReturnValue(of(myJob('ON_SITE', ['WORK_STARTED'])));

    click(fixture, "I've arrived");

    expect(api.updateJobStatus).toHaveBeenCalledWith('j1', 'ON_SITE', undefined);
    expect(buttons(fixture)).toEqual(['Start work']);
  });

  it('offers completing before going on hold', () => {
    const fixture = setup(of(myJob('WORK_STARTED', ['ON_HOLD', 'COMPLETE'])));

    expect(buttons(fixture)).toEqual(['Mark work complete', 'Put on hold']);
  });

  it('asks why before putting the job on hold', () => {
    const fixture = setup(of(myJob('WORK_STARTED', ['ON_HOLD', 'COMPLETE'])));
    api.updateJobStatus.mockReturnValue(of(myJob('ON_HOLD', ['WORK_RESUMED'])));

    click(fixture, 'Put on hold');
    click(fixture, 'Put on hold');
    expect(api.updateJobStatus).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain('Say why the job is being put on hold.');

    const reason = el(fixture).querySelector('textarea') as HTMLTextAreaElement;
    reason.value = 'Waiting for parts';
    reason.dispatchEvent(new Event('input'));
    click(fixture, 'Put on hold');

    expect(api.updateJobStatus).toHaveBeenCalledWith('j1', 'ON_HOLD', 'Waiting for parts');
    expect(buttons(fixture)).toEqual(['Resume work']);
  });

  it('asks once more before marking the work complete', () => {
    const fixture = setup(of(myJob('WORK_RESUMED', ['ON_HOLD', 'COMPLETE'])));
    api.updateJobStatus.mockReturnValue(of(myJob('COMPLETE', [])));

    click(fixture, 'Mark work complete');
    expect(api.updateJobStatus).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain("You can't undo this.");

    click(fixture, 'Mark work complete');

    expect(api.updateJobStatus).toHaveBeenCalledWith('j1', 'COMPLETE', undefined);
    expect(el(fixture).textContent).toContain('The job report comes next.');
  });

  it('shows where the job really is when the server refuses a step', () => {
    const fixture = setup(of(myJob('EN_ROUTE', ['ON_SITE'])));
    api.updateJobStatus.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'A job that is on site cannot move to on site.' },
          }),
      ),
    );
    api.myJob.mockReturnValue(of(myJob('ON_SITE', ['WORK_STARTED'])));

    click(fixture, "I've arrived");

    expect(el(fixture).textContent).toContain('A job that is on site cannot move to on site.');
    expect(buttons(fixture)).toContain('Start work');
  });

  it("says when the job isn't theirs", () => {
    const fixture = setup(throwError(() => new HttpErrorResponse({ status: 404 })));

    expect(el(fixture).textContent).toContain('Job not found');
  });
});
