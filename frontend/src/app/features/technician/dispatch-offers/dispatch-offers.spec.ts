import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { TechnicianApiService } from '../technician-api.service';
import { DispatchOffer } from '../technician.models';
import { DispatchOffers, timeLeft } from './dispatch-offers';

function offer(overrides: Partial<DispatchOffer> = {}): DispatchOffer {
  return {
    dispatchId: 'd1',
    dispatchType: 'ASSIGN',
    expiresAt: new Date(Date.now() + 7 * 60_000 + 5_000).toISOString(),
    ticketRef: '1042',
    clientName: 'Acme Mining',
    siteAddress: '12 Main Rd, Sandton',
    priority: 'HIGH',
    sla: '4 hrs',
    issueType: 'CCTV',
    description: 'Cameras on the east fence are down.',
    jobType: null,
    requiredSkills: null,
    notesForTechnician: 'Ask for the gate code at reception.',
    distanceKm: 3.4,
    ...overrides,
  };
}

describe('timeLeft', () => {
  const now = Date.parse('2026-10-25T10:00:00Z');

  it('counts down in minutes and seconds', () => {
    expect(timeLeft('2026-10-25T10:07:05Z', now)).toBe('7:05');
    expect(timeLeft('2026-10-25T10:00:09Z', now)).toBe('0:09');
  });

  it('is null once the window has passed', () => {
    expect(timeLeft('2026-10-25T10:00:00Z', now)).toBeNull();
    expect(timeLeft('2026-10-25T09:59:00Z', now)).toBeNull();
  });
});

describe('DispatchOffers', () => {
  let api: {
    offers: ReturnType<typeof vi.fn>;
    accept: ReturnType<typeof vi.fn>;
    decline: ReturnType<typeof vi.fn>;
  };

  function setup(offers: DispatchOffer[]): ComponentFixture<DispatchOffers> {
    api.offers.mockReturnValue(of(offers));
    TestBed.configureTestingModule({
      imports: [DispatchOffers],
      providers: [
        provideZonelessChangeDetection(),
        { provide: TechnicianApiService, useValue: api },
      ],
    });
    const fixture = TestBed.createComponent(DispatchOffers);
    fixture.detectChanges();
    return fixture;
  }

  function el(fixture: ComponentFixture<DispatchOffers>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function click(fixture: ComponentFixture<DispatchOffers>, label: string): void {
    const button = [...el(fixture).querySelectorAll('button')].find(
      (b) => b.textContent?.trim() === label,
    );
    button!.click();
    fixture.detectChanges();
  }

  beforeEach(() => {
    api = { offers: vi.fn(), accept: vi.fn(), decline: vi.fn() };
  });

  it('shows what the technician needs to decide, with the time left', () => {
    const fixture = setup([offer()]);
    const text = el(fixture).textContent ?? '';

    expect(text).toContain('Acme Mining');
    expect(text).toContain('12 Main Rd, Sandton');
    expect(text).toContain('High priority');
    expect(text).toContain('3.4 km away');
    expect(text).toContain('Ask for the gate code at reception.');
    expect(el(fixture).querySelector('.do-timer')?.textContent?.trim()).toMatch(/^7:0\d$/);
  });

  it('warns that a broadcast goes to whoever accepts first', () => {
    const fixture = setup([offer({ dispatchType: 'BROADCAST' })]);

    expect(el(fixture).textContent).toContain('The first to accept gets it.');
  });

  it('accepts in one tap and tells the page the technician is now on a job', () => {
    const fixture = setup([offer()]);
    const accepted = vi.fn();
    fixture.componentInstance.accepted.subscribe(accepted);
    api.accept.mockReturnValue(of({ dispatchId: 'd1', outcome: 'ACCEPTED' }));

    click(fixture, 'Accept');

    expect(api.accept).toHaveBeenCalledWith('d1');
    expect(accepted).toHaveBeenCalledWith(expect.objectContaining({ dispatchId: 'd1' }));
    expect(el(fixture).textContent).toContain("You've accepted the job at Acme Mining.");
    expect(el(fixture).querySelector('.do-card')).toBeNull();
  });

  it('declines without telling the page anything changed', () => {
    const fixture = setup([offer()]);
    const accepted = vi.fn();
    fixture.componentInstance.accepted.subscribe(accepted);
    api.decline.mockReturnValue(of({ dispatchId: 'd1', outcome: 'DECLINED' }));

    click(fixture, 'Decline');

    expect(api.decline).toHaveBeenCalledWith('d1');
    expect(accepted).not.toHaveBeenCalled();
    expect(el(fixture).querySelector('.do-card')).toBeNull();
  });

  it("says why when someone else got there first, and shows what's really left", () => {
    const fixture = setup([offer()]);
    api.accept.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'Another technician has already accepted this job.' },
          }),
      ),
    );
    api.offers.mockReturnValue(of([]));

    click(fixture, 'Accept');

    expect(el(fixture).textContent).toContain('Another technician has already accepted this job.');
    expect(el(fixture).querySelector('.do-card')).toBeNull();
  });

  it('does not show an offer whose window has already passed', () => {
    const fixture = setup([offer({ expiresAt: new Date(Date.now() - 1000).toISOString() })]);

    expect(el(fixture).querySelector('.do-card')).toBeNull();
    expect(el(fixture).textContent).toContain('No job offers right now.');
  });
});
