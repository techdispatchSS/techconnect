import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import { CreateIncidentRequest, Incident } from '../controller.models';
import { IncidentCreate } from './incident-create';

const CREATED = { id: 'new-1' } as Incident;

describe('IncidentCreate', () => {
  let fixture: ComponentFixture<IncidentCreate>;
  let el: HTMLElement;
  let component: IncidentCreate;
  let createIncident: ReturnType<typeof vi.fn>;
  let navigate: ReturnType<typeof vi.spyOn>;

  async function setup(impl: () => Observable<Incident> = () => of(CREATED)): Promise<void> {
    createIncident = vi.fn(impl);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ControllerApiService, useValue: { createIncident } },
      ],
    });

    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(IncidentCreate);
    el = fixture.nativeElement;
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function send(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('sends nothing without a client name and says why', async () => {
    await setup();
    await send();

    expect(createIncident).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Client name is required');
  });

  it('treats a whitespace-only client name as missing', async () => {
    await setup();
    type('#ic-client', '    ');
    await send();

    expect(createIncident).not.toHaveBeenCalled();
  });

  it('logs the incident with trimmed values and nulls for what was left blank', async () => {
    await setup();
    type('#ic-client', '  Phone Call Co  ');
    type('#ic-site', '14 Main Rd, Rosebank');
    type('#ic-phone', '+27 82 555 0100');
    type('#ic-issue', 'CCTV');
    type('#ic-description', 'Cameras offline.');
    await send();

    expect(createIncident).toHaveBeenCalledTimes(1);
    const request = createIncident.mock.calls[0][0] as CreateIncidentRequest;
    expect(request).toEqual({
      clientName: 'Phone Call Co',
      siteAddress: '14 Main Rd, Rosebank',
      siteLatitude: null,
      siteLongitude: null,
      contactName: null,
      contactPhone: '+27 82 555 0100',
      issueType: 'CCTV',
      priority: 'MEDIUM',
      slaHours: null,
      description: 'Cameras offline.',
      additionalNotes: null,
    });
  });

  it('takes the priority and an explicit response target from what was chosen', async () => {
    await setup();
    type('#ic-client', 'Urgent Co');
    (el.querySelectorAll('.ic-priority-option')[0] as HTMLButtonElement).click();
    const select = el.querySelector<HTMLSelectElement>('#ic-sla')!;
    select.value = '2';
    select.dispatchEvent(new Event('change'));
    await send();

    const request = createIncident.mock.calls[0][0] as CreateIncidentRequest;
    expect(request.priority).toBe('HIGH');
    expect(request.slaHours).toBe(2);
  });

  it('goes to the new incident in the queue once it is logged', async () => {
    await setup();
    type('#ic-client', 'Phone Call Co');
    await send();

    expect(navigate).toHaveBeenCalledWith(['/incidents/queue'], {
      queryParams: { selected: 'new-1' },
    });
  });

  it('refuses a contact number with letters in it', async () => {
    await setup();
    type('#ic-client', 'Phone Call Co');
    type('#ic-phone', 'call me');
    await send();

    expect(createIncident).not.toHaveBeenCalled();
    expect(el.textContent).toContain('digits, spaces');
  });

  it("shows the server's own reason and stays on the form when it refuses", async () => {
    await setup(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { error: 'Provide both a latitude and a longitude, or neither.' },
          }),
      ),
    );
    type('#ic-client', 'Phone Call Co');
    await send();

    expect(el.querySelector('[role="alert"]')!.textContent).toContain('latitude and a longitude');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not send twice while a request is in flight', async () => {
    const { Subject } = await import('rxjs');
    const pending = new Subject<Incident>();
    await setup(() => pending);
    type('#ic-client', 'Phone Call Co');

    await send();
    await send();

    expect(createIncident).toHaveBeenCalledTimes(1);
  });

  const PICKED = {
    country: 'ZA',
    street: '83 Rivonia Rd',
    suburb: 'Sandton',
    city: 'Johannesburg',
    province: 'GAUTENG',
    postalCode: '2196',
    latitude: -26.1076,
    longitude: 28.0567,
  };

  it('keeps the map position of an address picked from the suggestions', async () => {
    await setup();
    type('#ic-client', 'Mapped Co');

    component.onAddressPicked(PICKED);
    await fixture.whenStable();
    expect(el.textContent).toContain('Map location found');

    await send();
    const request = createIncident.mock.calls[0][0] as CreateIncidentRequest;
    expect(request.siteAddress).toBe('83 Rivonia Rd, Sandton, Johannesburg, 2196');
    expect(request.siteLatitude).toBe(-26.1076);
    expect(request.siteLongitude).toBe(28.0567);
  });

  it('drops the map position once the address is typed over', async () => {
    await setup();
    type('#ic-client', 'Mapped Co');
    component.onAddressPicked(PICKED);
    await fixture.whenStable();

    // The pin was placed for the old address; it no longer describes this one.
    type('#ic-site', '12 Somewhere Else');
    await fixture.whenStable();
    expect(el.textContent).not.toContain('Map location found');
    expect(el.textContent).toContain("won't be calculated");

    await send();
    const request = createIncident.mock.calls[0][0] as CreateIncidentRequest;
    expect(request.siteAddress).toBe('12 Somewhere Else');
    expect(request.siteLatitude).toBeNull();
    expect(request.siteLongitude).toBeNull();
  });
});
