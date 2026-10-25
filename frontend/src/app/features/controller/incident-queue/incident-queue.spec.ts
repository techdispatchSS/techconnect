import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';

import { ControllerApiService } from '../controller-api.service';
import {
  DispatchSummary,
  FreshdeskStatus,
  Incident,
  IncidentKpis,
  IncidentListFilters,
  PageResponse,
} from '../controller.models';
import { FreshdeskStatusService } from '../freshdesk-status.service';
import { NewIncidentNotifierService } from '../new-incident-notifier.service';
import { IncidentQueue } from './incident-queue';

function incident(over: Partial<Incident> = {}): Incident {
  return {
    id: 'i1',
    ticketRef: 'INC-0001',
    clientName: 'Acme Corp',
    siteAddress: '1 Main Rd',
    siteLatitude: null,
    siteLongitude: null,
    issueType: 'Networking',
    priority: 'HIGH',
    sla: '4 hrs',
    status: 'NEW',
    overdue: false,
    contactName: 'Jo',
    contactPhone: '082 555 0100',
    description: 'Core switch is down.',
    additionalNotes: 'Gate code 1234.',
    slaDueAt: null,
    createdAt: new Date().toISOString(),
    requiredSkills: [],
    hasDispatch: false,
    dispatch: null,
    ...over,
  };
}

function page(content: Incident[], totalPages = 1, number = 0): PageResponse<Incident> {
  return {
    content,
    page: number,
    size: 20,
    totalElements: content.length * totalPages,
    totalPages,
  };
}

function summary(over: Partial<DispatchSummary> = {}): DispatchSummary {
  return {
    id: 'd1',
    type: 'BROADCAST',
    status: 'PENDING',
    sentAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 7 * 60_000).toISOString(),
    invited: 3,
    pending: 3,
    declined: 0,
    noResponse: 0,
    acceptedTechnicianName: null,
    notificationCount: 1,
    lastNotifiedAt: new Date().toISOString(),
    ...over,
  };
}

const KPIS: IncidentKpis = { newCount: 4, unassignedCount: 3, inProgressCount: 2, overdueCount: 1 };

const SIMULATED: FreshdeskStatus = {
  mode: 'SIMULATED',
  simulation: true,
  lastSyncAt: null,
  lastSuccessAt: null,
  lastError: null,
  pushMode: 'DEMO',
};

describe('IncidentQueue', () => {
  let fixture: ComponentFixture<IncidentQueue>;
  let el: HTMLElement;
  let listIncidents: ReturnType<typeof vi.fn>;
  let simulateFreshdeskTicket: ReturnType<typeof vi.fn>;
  let resendDispatch: ReturnType<typeof vi.fn>;
  let notifierRefresh: ReturnType<typeof vi.fn>;
  let status: ReturnType<typeof signal<FreshdeskStatus | null>>;

  async function setup(
    rows: Incident[] = [incident()],
    options: {
      totalPages?: number;
      freshdesk?: FreshdeskStatus | null;
      /** A `?selected=` deep link, and the incident the server returns for it. */
      selected?: Incident;
    } = {},
  ): Promise<void> {
    listIncidents = vi.fn((filters: IncidentListFilters) =>
      of(page(rows, options.totalPages ?? 1, filters.page)),
    );
    simulateFreshdeskTicket = vi.fn(() => of({ ticketId: 'SIM-1', created: 1, updated: 0 }));
    resendDispatch = vi.fn(() =>
      of({ notified: 2, notificationCount: 2, lastNotifiedAt: new Date().toISOString() }),
    );
    notifierRefresh = vi.fn();
    status = signal<FreshdeskStatus | null>(
      options.freshdesk === undefined ? null : options.freshdesk,
    );

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        ...(options.selected
          ? [
              {
                provide: ActivatedRoute,
                useValue: {
                  queryParamMap: of(convertToParamMap({ selected: options.selected.id })),
                },
              },
            ]
          : []),
        {
          provide: ControllerApiService,
          useValue: {
            getIncident: () => of(options.selected),
            listIncidents,
            kpis: () => of(KPIS),
            simulateFreshdeskTicket,
            resendDispatch,
          },
        },
        { provide: FreshdeskStatusService, useValue: { status, refresh: vi.fn() } },
        { provide: NewIncidentNotifierService, useValue: { refresh: notifierRefresh } },
      ],
    });

    fixture = TestBed.createComponent(IncidentQueue);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  function lastFilters(): IncidentListFilters {
    return listIncidents.mock.calls.at(-1)![0] as IncidentListFilters;
  }

  function button(label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) =>
      b.textContent!.trim().startsWith(label),
    ) as HTMLButtonElement;
  }

  it('shows the incidents and the counts', async () => {
    await setup([incident({ ticketRef: 'INC-0001', clientName: 'Acme Corp' })]);

    expect(el.textContent).toContain('INC-0001');
    expect(el.textContent).toContain('Acme Corp');
    expect(el.querySelector('.iq-kpi-value')!.textContent).toContain('4');
  });

  it('shows what is wrong and the additional notes separately', async () => {
    await setup();

    const detail = el.querySelector('.iq-detail')!.textContent!;
    expect(detail).toContain('Core switch is down.');
    expect(detail).toContain('Gate code 1234.');
  });

  it('offers paging only when there is more than one page, and asks the server for the next', async () => {
    await setup([incident()], { totalPages: 3 });

    expect(el.textContent).toContain('Page 1 of 3');
    button('Next').click();
    await fixture.whenStable();

    expect(lastFilters().page).toBe(1);
    expect(el.textContent).toContain('Page 2 of 3');
    expect((button('Previous') as HTMLButtonElement).disabled).toBe(false);
  });

  it('has no pager for a single page', async () => {
    await setup([incident()], { totalPages: 1 });

    expect(el.querySelector('.iq-pager')).toBeNull();
  });

  it('filters by priority on the server and starts again from the first page', async () => {
    await setup([incident()], { totalPages: 3 });
    button('Next').click();
    await fixture.whenStable();

    const select = el.querySelector<HTMLSelectElement>('.iq-filter select')!;
    select.value = 'LOW';
    select.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(lastFilters().priority).toBe('LOW');
    expect(lastFilters().page).toBe(0);
  });

  it('lets the keyboard select a row through its ticket button', async () => {
    await setup([
      incident({ id: 'a', ticketRef: 'INC-AAAA', clientName: 'First Co' }),
      incident({ id: 'b', ticketRef: 'INC-BBBB', clientName: 'Second Co' }),
    ]);
    expect(el.querySelector('.iq-detail-ticket')!.textContent).toContain('INC-AAAA');

    const buttons = el.querySelectorAll<HTMLButtonElement>('.iq-ticket-btn');
    expect(buttons).toHaveLength(2);
    buttons[1].click();
    await fixture.whenStable();

    expect(el.querySelector('.iq-detail-ticket')!.textContent).toContain('INC-BBBB');
    expect(buttons[1].getAttribute('aria-current')).toBe('true');
  });

  it('has no demo controls unless the backend says simulation is on', async () => {
    await setup([incident()], {
      freshdesk: { ...SIMULATED, mode: 'NOT_CONNECTED', simulation: false },
    });

    expect(el.querySelector('.iq-sim')).toBeNull();
    expect(button('Simulate')).toBeUndefined();
  });

  it('raises a simulated ticket and shows it at once', async () => {
    await setup([incident()], { freshdesk: SIMULATED });
    const callsBefore = listIncidents.mock.calls.length;

    button('Simulate Freshdesk ticket').click();
    await fixture.whenStable();

    expect(simulateFreshdeskTicket).toHaveBeenCalledWith(false);
    expect(listIncidents.mock.calls.length).toBeGreaterThan(callsBefore);
    expect(notifierRefresh).toHaveBeenCalled();
  });

  it('can raise an overdue ticket and jumps to the Overdue tab to show it', async () => {
    await setup([incident()], { freshdesk: SIMULATED });

    button('Simulate overdue ticket').click();
    await fixture.whenStable();

    expect(simulateFreshdeskTicket).toHaveBeenCalledWith(true);
    expect(lastFilters().status).toBe('OVERDUE');
  });

  describe('after a dispatch has been sent', () => {
    it('says how many technicians it went to and that it is awaiting confirmation', async () => {
      await setup([incident({ hasDispatch: true, dispatch: summary({ invited: 3, pending: 3 }) })]);

      const detail = el.querySelector('.iq-dispatch')!.textContent!;
      expect(detail).toContain('Sent to 3 technicians — awaiting confirmation');
      expect(detail).toContain('3 yet to answer');
      expect(el.querySelector('.iq-dispatch-chip')!.textContent).toContain('Sent to 3 · awaiting');
    });

    it('no longer offers to create a dispatch, but to follow the one that is out', async () => {
      await setup([incident({ hasDispatch: true, dispatch: summary() })]);

      expect(button('Create dispatch')).toBeUndefined();
      expect(button('View dispatch status')).toBeDefined();
    });

    it('offers a fresh dispatch when the last one lapsed', async () => {
      await setup([
        incident({
          hasDispatch: true,
          dispatch: summary({ status: 'EXPIRED', pending: 0, noResponse: 3 }),
        }),
      ]);

      expect(button('Dispatch again')).toBeDefined();
      expect(el.querySelector('.iq-dispatch')!.textContent).toContain('Nobody accepted in time');
      expect(button('Resend notification')).toBeUndefined();
    });

    it('says who took it once accepted', async () => {
      await setup([
        incident({
          hasDispatch: true,
          dispatch: summary({
            status: 'ACCEPTED',
            pending: 0,
            noResponse: 2,
            acceptedTechnicianName: 'Thabo M',
          }),
        }),
      ]);

      expect(el.querySelector('.iq-dispatch')!.textContent).toContain('Accepted by Thabo M');
      expect(button('Resend notification')).toBeUndefined();
    });

    it('resends the notification for that dispatch and confirms how many were told', async () => {
      await setup([incident({ hasDispatch: true, dispatch: summary({ id: 'dispatch-9' }) })]);

      button('Resend notification').click();
      await fixture.whenStable();

      expect(resendDispatch).toHaveBeenCalledWith('dispatch-9');
      expect(el.querySelector('.iq-dispatch-feedback')!.textContent).toContain(
        'sent again to 2 technicians',
      );
    });

    it("shows the server's reason when a resend is refused", async () => {
      await setup([incident({ hasDispatch: true, dispatch: summary() })]);
      resendDispatch.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 409,
              error: {
                error: 'Technicians were notified moments ago. You can resend in 41 seconds.',
              },
            }),
        ),
      );

      button('Resend notification').click();
      await fixture.whenStable();

      expect(el.querySelector('.iq-dispatch-feedback')!.textContent).toContain('resend in 41');
    });
  });

  describe('returning to a ticket that has just been dispatched', () => {
    const dispatched = incident({
      id: 'sent-1',
      ticketRef: 'INC-SENT',
      status: 'IN_PROGRESS',
      hasDispatch: true,
      dispatch: summary({ invited: 2, pending: 2 }),
    });

    it('opens the tab it is in, so it does not vanish from the list', async () => {
      await setup([dispatched], { selected: dispatched });

      expect(lastFilters().status).toBe('IN_PROGRESS');
      expect(el.querySelector('.iq-tab--active')!.textContent).toContain('In progress');
    });

    it("shows that it has been sent, not the next ticket's Create dispatch", async () => {
      await setup([incident({ id: 'next', ticketRef: 'INC-NEXT' }), dispatched], {
        selected: dispatched,
      });

      expect(el.querySelector('.iq-detail-ticket')!.textContent).toContain('INC-SENT');
      expect(el.querySelector('.iq-dispatch')!.textContent).toContain(
        'Sent to 2 technicians — awaiting confirmation',
      );
      expect(el.querySelector('.iq-detail .controller-btn-primary')!.textContent).toContain(
        'View dispatch status',
      );
    });
  });

  describe('a deep link to an incident that is not on the loaded page', () => {
    // Logging a Medium incident sorts it behind the urgent ones, so the list's first page does
    // not contain it; the server still returns it by id.
    const urgent = incident({
      id: 'urgent-1',
      ticketRef: 'INC-4587',
      priority: 'HIGH',
      status: 'IN_PROGRESS',
      hasDispatch: true,
      dispatch: summary({ status: 'EXPIRED', pending: 0, noResponse: 3 }),
    });
    const logged = incident({ id: 'logged-1', ticketRef: 'INC-LOGGED', priority: 'MEDIUM' });

    it('shows that incident in the panel, not the first row', async () => {
      await setup([urgent], { selected: logged, totalPages: 2 });

      expect(el.querySelector('.iq-detail-ticket')!.textContent).toContain('INC-LOGGED');
      expect(el.querySelector('.iq-detail-offpage')).not.toBeNull();
    });

    it('offers the action for that incident, and takes it to that incident', async () => {
      await setup([urgent], { selected: logged, totalPages: 2 });
      const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

      // The first row's expired dispatch would read "Dispatch again"; this one has none.
      expect(button('Dispatch again')).toBeUndefined();
      expect(el.querySelector('.iq-detail .controller-btn-primary')!.textContent).toContain(
        'Create dispatch',
      );

      button('Create dispatch').click();
      expect(navigate).toHaveBeenCalledWith(['/incidents', 'dispatch', 'logged-1']);
    });

    it('gives the panel to a row once one is chosen', async () => {
      await setup([urgent], { selected: logged, totalPages: 2 });

      el.querySelector<HTMLButtonElement>('.iq-ticket-btn')!.click();
      await fixture.whenStable();

      expect(el.querySelector('.iq-detail-ticket')!.textContent).toContain('INC-4587');
      expect(el.querySelector('.iq-detail-offpage')).toBeNull();
    });
  });
  describe('on a phone, where the panel is stacked under the list', () => {
    function matchMedia(stacked: boolean): void {
      vi.stubGlobal('matchMedia', (query: string) => ({
        matches: stacked && query.includes('1100px'),
        media: query,
      }));
    }

    afterEach(() => vi.unstubAllGlobals());

    it('scrolls the panel into view when a row is tapped, and back to the list on request', async () => {
      matchMedia(true);
      const scrollIntoView = vi.fn();
      Element.prototype.scrollIntoView = scrollIntoView;
      await setup([
        incident({ id: 'a', ticketRef: 'INC-A' }),
        incident({ id: 'b', ticketRef: 'INC-B' }),
      ]);

      Array.from(el.querySelectorAll<HTMLElement>('tbody tr'))[1].click();
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));

      expect(el.querySelector('.iq-detail-ticket')!.textContent).toContain('INC-B');
      expect(scrollIntoView).toHaveBeenCalledTimes(1);
      expect(scrollIntoView.mock.contexts[0]).toBe(el.querySelector('.iq-detail'));

      button('↑ Back to list').click();
      await fixture.whenStable();

      expect(scrollIntoView).toHaveBeenCalledTimes(2);
      expect(scrollIntoView.mock.contexts[1]).toBe(el.querySelector('.iq-queue'));
    });

    it('does not move the page when the panel sits beside the list', async () => {
      matchMedia(false);
      const scrollIntoView = vi.fn();
      Element.prototype.scrollIntoView = scrollIntoView;
      await setup([incident({ id: 'a' }), incident({ id: 'b', ticketRef: 'INC-B' })]);

      Array.from(el.querySelectorAll<HTMLElement>('tbody tr'))[1].click();
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve));

      expect(scrollIntoView).not.toHaveBeenCalled();
    });
  });
});
