import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { DeviceLocationService } from '../device-location.service';
import { TechnicianApiService } from '../technician-api.service';
import { Coordinates, MyAvailability } from '../technician.models';
import { TechnicianHome } from './technician-home';

function availability(overrides: Partial<MyAvailability> = {}): MyAvailability {
  return {
    status: 'OFFLINE',
    onJob: false,
    lastLatitude: null,
    lastLongitude: null,
    lastLocationAt: null,
    ...overrides,
  };
}

const HERE: Coordinates = { latitude: -26.1076, longitude: 28.0567 };

describe('TechnicianHome', () => {
  let api: {
    availability: ReturnType<typeof vi.fn>;
    setAvailable: ReturnType<typeof vi.fn>;
    reportLocation: ReturnType<typeof vi.fn>;
    offers: ReturnType<typeof vi.fn>;
  };
  let locate: ReturnType<typeof vi.fn>;

  function setup(start: Observable<MyAvailability>): ComponentFixture<TechnicianHome> {
    api.availability.mockReturnValue(start);
    TestBed.configureTestingModule({
      imports: [TechnicianHome],
      providers: [
        provideZonelessChangeDetection(),
        { provide: TechnicianApiService, useValue: api },
        { provide: DeviceLocationService, useValue: { current: locate } },
        { provide: AuthService, useValue: { displayName: signal('Thandi') } },
      ],
    });
    const fixture = TestBed.createComponent(TechnicianHome);
    fixture.detectChanges();
    return fixture;
  }

  function text(fixture: ComponentFixture<TechnicianHome>): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  function button(fixture: ComponentFixture<TechnicianHome>): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement).querySelector('.th-toggle') as HTMLButtonElement;
  }

  beforeEach(() => {
    api = {
      availability: vi.fn(),
      setAvailable: vi.fn(),
      reportLocation: vi.fn((c: Coordinates) =>
        of(availability({ lastLatitude: c.latitude, lastLongitude: c.longitude })),
      ),
      offers: vi.fn(() => of([])),
    };
    locate = vi.fn(() => Promise.resolve(HERE));
  });

  it('shows an off-duty technician and shares their location when the page opens', async () => {
    const fixture = setup(of(availability()));
    await fixture.whenStable();

    expect(text(fixture)).toContain("You're off duty");
    expect(button(fixture).textContent).toContain('Go on duty');
    expect(api.reportLocation).toHaveBeenCalledWith(HERE);
  });

  it('goes on duty in one tap', async () => {
    const fixture = setup(of(availability()));
    api.setAvailable.mockReturnValue(of(availability({ status: 'AVAILABLE' })));
    api.reportLocation.mockReturnValue(of(availability({ status: 'AVAILABLE' })));

    button(fixture).click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(api.setAvailable).toHaveBeenCalledWith(true);
    expect(text(fixture)).toContain("You're on duty");
    expect(button(fixture).textContent).toContain('Go off duty');
  });

  it("can't be toggled while the technician is on a job", () => {
    const fixture = setup(of(availability({ status: 'ON_JOB', onJob: true })));

    expect(text(fixture)).toContain("You're on a job");
    expect(button(fixture).disabled).toBe(true);
    fixture.componentInstance.toggle();
    expect(api.setAvailable).not.toHaveBeenCalled();
  });

  it("says what the server said when the change isn't allowed", async () => {
    const fixture = setup(of(availability({ status: 'AVAILABLE' })));
    api.setAvailable.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 409,
            error: { error: 'Finish your current job before going off duty.' },
          }),
      ),
    );

    button(fixture).click();
    fixture.detectChanges();

    expect(text(fixture)).toContain('Finish your current job before going off duty.');
    expect(text(fixture)).toContain("You're on duty");
  });

  it('asks for location to be allowed when the phone refuses it', async () => {
    locate.mockReturnValue(Promise.reject(new Error('denied')));
    const fixture = setup(of(availability({ status: 'AVAILABLE' })));
    await fixture.whenStable();
    fixture.detectChanges();

    expect(text(fixture)).toContain('Location is off');
    expect(api.reportLocation).not.toHaveBeenCalled();
  });

  it('offers a retry when the status cannot be loaded', () => {
    const fixture = setup(throwError(() => new Error('offline')));

    expect(text(fixture)).toContain("Couldn't load your status");
    api.availability.mockReturnValue(of(availability()));
    button(fixture).click();
    fixture.detectChanges();

    expect(text(fixture)).toContain("You're off duty");
  });
});
