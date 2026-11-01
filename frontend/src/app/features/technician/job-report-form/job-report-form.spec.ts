import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { TechnicianApiService } from '../technician-api.service';
import { MyJob } from '../technician.models';
import { JobReportForm } from './job-report-form';

describe('JobReportForm', () => {
  let submitReport: ReturnType<typeof vi.fn>;

  function setup(): ComponentFixture<JobReportForm> {
    TestBed.configureTestingModule({
      imports: [JobReportForm],
      providers: [
        provideZonelessChangeDetection(),
        { provide: TechnicianApiService, useValue: { submitReport } },
      ],
    });
    const fixture = TestBed.createComponent(JobReportForm);
    fixture.componentRef.setInput('jobId', 'j1');
    fixture.detectChanges();
    return fixture;
  }

  function el(fixture: ComponentFixture<JobReportForm>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function send(fixture: ComponentFixture<JobReportForm>): void {
    (el(fixture).querySelector('button[type="submit"]') as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  function fillRequired(fixture: ComponentFixture<JobReportForm>): void {
    fixture.componentInstance.form.patchValue({
      resolutionNotes: '  Replaced the PoE switch. ',
      rootCause: 'Failed switch.',
      labourHours: 2.5,
    });
  }

  beforeEach(() => {
    submitReport = vi.fn();
  });

  it('refuses to send until the required fields are filled, and says which', () => {
    const fixture = setup();

    send(fixture);

    expect(submitReport).not.toHaveBeenCalled();
    const text = el(fixture).textContent ?? '';
    expect(text).toContain('Say what was done.');
    expect(text).toContain('Say what caused it.');
    expect(text).toContain('Enter the hours worked');
  });

  it('does not accept only spaces as an answer', () => {
    const fixture = setup();
    fillRequired(fixture);
    fixture.componentInstance.form.patchValue({ rootCause: '   ' });

    send(fixture);

    expect(submitReport).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain('Say what caused it.');
  });

  it('submits the tidied report and hands the page the submitted job', () => {
    const job = { nextSteps: [] } as unknown as MyJob;
    submitReport.mockReturnValue(of(job));
    const fixture = setup();
    const submitted = vi.fn();
    fixture.componentInstance.submitted.subscribe(submitted);
    fillRequired(fixture);

    fixture.componentInstance.addPart();
    fixture.componentInstance.parts
      .at(0)
      .patchValue({ name: ' PoE switch ', quantity: 2, serialNumber: '  ' });
    send(fixture);

    expect(submitReport).toHaveBeenCalledWith('j1', {
      resolutionNotes: 'Replaced the PoE switch.',
      rootCause: 'Failed switch.',
      recommendations: null,
      labourHours: 2.5,
      parts: [{ name: 'PoE switch', quantity: 2, serialNumber: null }],
    });
    expect(submitted).toHaveBeenCalledWith(job);
  });

  it('holds back a part with no name until it is named or removed', () => {
    submitReport.mockReturnValue(of({} as MyJob));
    const fixture = setup();
    fillRequired(fixture);
    fixture.componentInstance.addPart();
    fixture.detectChanges();

    send(fixture);
    expect(submitReport).not.toHaveBeenCalled();
    expect(el(fixture).textContent).toContain('Name the part, or remove it.');

    (el(fixture).querySelector('.jr-remove') as HTMLButtonElement).click();
    fixture.detectChanges();
    send(fixture);

    expect(submitReport).toHaveBeenCalledTimes(1);
  });

  it('keeps what was typed when the connection fails', () => {
    submitReport.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 0 })));
    const fixture = setup();
    const stale = vi.fn();
    fixture.componentInstance.stale.subscribe(stale);
    fillRequired(fixture);

    send(fixture);

    expect(el(fixture).textContent).toContain('Your answers are still here');
    expect(fixture.componentInstance.form.value.rootCause).toBe('Failed switch.');
    expect(stale).not.toHaveBeenCalled();
  });

  it('says why, and tells the page, when the job can no longer be submitted', () => {
    submitReport.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'Only a completed job can be submitted. This one is submitted.' },
          }),
      ),
    );
    const fixture = setup();
    const stale = vi.fn();
    fixture.componentInstance.stale.subscribe(stale);
    fillRequired(fixture);

    send(fixture);

    expect(el(fixture).textContent).toContain('Only a completed job can be submitted.');
    expect(stale).toHaveBeenCalled();
  });
});
