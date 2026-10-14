import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { Report } from '../controller.models';
import { Reports, isoDay } from './reports';

function report(overrides: Partial<Report> = {}): Report {
  return {
    from: '2026-10-01',
    to: '2026-10-03',
    zone: 'Africa/Johannesburg',
    incidents: { created: 12, closed: 9, open: 5, overdueNow: 2 },
    sla: { withinSla: 7, late: 2, compliancePercent: 77.8 },
    dispatching: {
      dispatches: 10,
      accepted: 8,
      expired: 2,
      invitations: 24,
      accepts: 8,
      declines: 6,
      noResponses: 10,
      averageMinutesToAccept: 6.5,
      acceptRatePercent: 80,
    },
    jobs: {
      opened: 8,
      submitted: 7,
      closed: 6,
      averageHoursOnSite: 1.8,
      averageLabourHours: 2.2,
      averageHoursToClose: 5.4,
    },
    byPriority: [
      { priority: 'HIGH', created: 4, closed: 3, withinSla: 2, late: 1 },
      { priority: 'MEDIUM', created: 5, closed: 4, withinSla: 4, late: 0 },
      { priority: 'LOW', created: 3, closed: 2, withinSla: 1, late: 1 },
    ],
    byTechnician: [
      {
        technicianId: 't1',
        name: 'Thandi Mokoena',
        invited: 6,
        accepted: 4,
        declined: 1,
        noResponse: 1,
        jobs: 4,
        jobsClosed: 3,
        averageLabourHours: 2.5,
      },
    ],
    daily: [
      { date: '2026-10-01', created: 4, closed: 2 },
      { date: '2026-10-02', created: 5, closed: 4 },
      { date: '2026-10-03', created: 3, closed: 3 },
    ],
    ...overrides,
  };
}

describe('Reports', () => {
  let fixture: ComponentFixture<Reports>;
  let el: HTMLElement;
  let reportCall: ReturnType<typeof vi.fn>;
  let reportCsv: ReturnType<typeof vi.fn>;

  async function setup(impl: () => unknown = () => of(report())): Promise<void> {
    reportCall = vi.fn(impl);
    reportCsv = vi.fn(() => of(new Blob(['a,b'], { type: 'text/csv' })));
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: ControllerApiService, useValue: { report: reportCall, reportCsv } },
      ],
    });
    fixture = TestBed.createComponent(Reports);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  it('asks for the last 30 days in the reader’s own zone', async () => {
    await setup();

    const today = isoDay(new Date());
    const [from, to, zone] = reportCall.mock.calls[0] as [string, string, string];
    expect(to).toBe(today);
    const start = new Date();
    start.setDate(start.getDate() - 29);
    expect(from).toBe(isoDay(start));
    expect(zone).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC');
  });

  it('shows the headline figures', async () => {
    await setup();

    expect(el.textContent).toContain('77.8%');
    expect(el.textContent).toContain('7 on time · 2 late');
    expect(el.textContent).toContain('6.5 min');
    expect(el.textContent).toContain('Thandi Mokoena');
  });

  it('shows a dash, not 0%, for a measure with nothing behind it', async () => {
    await setup(() =>
      of(
        report({
          sla: { withinSla: 0, late: 0, compliancePercent: null },
          dispatching: {
            ...report().dispatching,
            averageMinutesToAccept: null,
            acceptRatePercent: null,
          },
          byTechnician: [],
        }),
      ),
    );

    const values = Array.from(el.querySelectorAll('.rp-kpi-value')).map((n) =>
      n.textContent!.trim(),
    );
    expect(values[0]).toBe('—');
    expect(values[2]).toBe('—');
    expect(el.textContent).toContain('No technician was dispatched in this range.');
  });

  it('re-reads when a preset is chosen', async () => {
    await setup();
    reportCall.mockClear();

    const sevenDays = Array.from(el.querySelectorAll<HTMLButtonElement>('.rp-presets button')).find(
      (b) => b.textContent!.includes('7 days'),
    )!;
    sevenDays.click();
    await fixture.whenStable();

    const start = new Date();
    start.setDate(start.getDate() - 6);
    expect(reportCall).toHaveBeenCalledWith(isoDay(start), isoDay(new Date()), expect.any(String));
  });

  it('shows the server’s reason when the range is refused', async () => {
    await setup(() =>
      throwError(() => ({ status: 400, error: { error: 'Pick a range of a year or less.' } })),
    );
    expect(el.textContent).toContain('Pick a range of a year or less.');
  });

  it('says it could not load when the server is down', async () => {
    await setup(() => throwError(() => ({ status: 503 })));
    expect(el.textContent).toContain("Couldn't load the report");
  });

  it('downloads the same range as a CSV', async () => {
    await setup();
    const create = vi.fn(() => 'blob:csv');
    const revoke = vi.fn();
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    Array.from(el.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent!.includes('Download CSV'))!
      .click();
    await fixture.whenStable();

    expect(reportCsv).toHaveBeenCalledTimes(1);
    expect(create).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    click.mockRestore();
  });
});
