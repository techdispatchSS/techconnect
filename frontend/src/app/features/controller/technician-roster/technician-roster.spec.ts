import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { PageResponse, TechnicianRosterEntry } from '../controller.models';
import { TechnicianRoster } from './technician-roster';

function tech(overrides: Partial<TechnicianRosterEntry> = {}): TechnicianRosterEntry {
  return {
    id: 't1',
    name: 'Thandi Mokoena',
    email: 'thandi@example.com',
    phone: '082 123 4567',
    accountStatus: 'ACTIVE',
    availability: 'AVAILABLE',
    city: 'Johannesburg',
    region: 'GAUTENG',
    country: 'ZA',
    skills: ['CCTV', 'Fibre'],
    currentJob: null,
    jobsClosed: 12,
    lastSeenAt: null,
    ...overrides,
  };
}

function page(rows: TechnicianRosterEntry[]): PageResponse<TechnicianRosterEntry> {
  return { content: rows, page: 0, size: 20, totalElements: rows.length, totalPages: 1 };
}

describe('TechnicianRoster', () => {
  let fixture: ComponentFixture<TechnicianRoster>;
  let el: HTMLElement;
  let listTechnicians: ReturnType<typeof vi.fn>;

  async function setup(rows: TechnicianRosterEntry[] = [tech()], fail = false): Promise<void> {
    // The debounce reads the clock as well as the interval, so the clock is faked with it.
    vi.useFakeTimers({ toFake: ['setInterval', 'clearInterval', 'Date'] });
    listTechnicians = vi.fn(() => (fail ? throwError(() => new Error('down')) : of(page(rows))));
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ControllerApiService, useValue: { listTechnicians } },
      ],
    });
    fixture = TestBed.createComponent(TechnicianRoster);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  afterEach(() => vi.useRealTimers());

  it('lists who is there, what they can do and where they are based', async () => {
    await setup();

    expect(listTechnicians).toHaveBeenCalledWith('', '', 0, 20);
    expect(el.textContent).toContain('Thandi Mokoena');
    expect(el.textContent).toContain('thandi@example.com');
    expect(el.textContent).toContain('Available');
    expect(el.textContent).toContain('CCTV');
    expect(el.textContent).toContain('Johannesburg, Gauteng');
  });

  it('names the job a busy technician is on, and links to it', async () => {
    await setup([
      tech({
        availability: 'ON_JOB',
        currentJob: {
          jobId: 'j9',
          ticketRef: 'SIM-1001',
          clientName: 'Acme Logistics',
          status: 'ON_SITE',
        },
      }),
    ]);

    expect(el.textContent).toContain('SIM-1001 · Acme Logistics');
    expect(el.textContent).toContain('On site');
    expect(el.querySelector('a.tr-job')!.getAttribute('href')).toBe('/incidents/jobs/j9');
  });

  it('shows the country only when it is not South Africa', async () => {
    await setup([
      tech({ id: 't2', name: 'Wanjiru', city: 'Nairobi', region: 'Nairobi County', country: 'KE' }),
    ]);
    expect(el.textContent).toContain('Nairobi, Nairobi County, Kenya');
  });

  it('flags someone who has not activated their account', async () => {
    await setup([tech({ accountStatus: 'PENDING_ACTIVATION' })]);
    expect(el.textContent).toContain("Hasn't activated their account");
  });

  it('is read-only: there is nothing here that changes a technician', async () => {
    await setup();
    const actions = Array.from(el.querySelectorAll('button')).map((b) => b.textContent!.trim());
    expect(actions.filter((a) => /edit|delete|disable|add|offboard|reset/i.test(a))).toEqual([]);
  });

  it('filters by availability on the server', async () => {
    await setup();
    listTechnicians.mockClear();

    const select = el.querySelector<HTMLSelectElement>('select')!;
    select.value = 'ON_JOB';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(listTechnicians).toHaveBeenCalledWith('', 'ON_JOB', 0, 20);
  });

  it('searches on the server after the typing settles', async () => {
    await setup();
    listTechnicians.mockClear();

    const input = el.querySelector<HTMLInputElement>('input')!;
    input.value = 'cctv';
    input.dispatchEvent(new Event('input'));
    vi.advanceTimersByTime(400);
    await fixture.whenStable();

    expect(listTechnicians).toHaveBeenCalledWith('cctv', '', 0, 20);
  });

  it('says it could not load, rather than showing an empty roster', async () => {
    await setup([], true);
    expect(el.textContent).toContain("Couldn't load the technicians");
  });
});
