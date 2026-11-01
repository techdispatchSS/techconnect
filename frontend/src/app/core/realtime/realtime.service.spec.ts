import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Client, StompConfig } from '@stomp/stompjs';

import { UserRole } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';
import { RealtimeEvent, RealtimeService, STOMP_CLIENT_FACTORY } from './realtime.service';

const EVENT: RealtimeEvent = {
  type: 'JOB_CHANGED',
  incidentId: 'i1',
  dispatchId: 'd1',
  jobId: 'j1',
  status: 'ON_SITE',
};

describe('RealtimeService', () => {
  let config: StompConfig | null;
  let activate: ReturnType<typeof vi.fn>;
  let deactivate: ReturnType<typeof vi.fn>;
  let subscribe: ReturnType<typeof vi.fn>;

  function setup(role: UserRole | null): RealtimeService {
    config = null;
    activate = vi.fn();
    deactivate = vi.fn();
    subscribe = vi.fn();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: AuthService, useValue: { role: () => role, token: () => 'the-token' } },
        {
          provide: STOMP_CLIENT_FACTORY,
          useValue: (given: StompConfig) => {
            config = given;
            return { activate, deactivate, subscribe } as unknown as Client;
          },
        },
      ],
    });
    return TestBed.inject(RealtimeService);
  }

  function connected(): void {
    (config!.onConnect as () => void)();
  }

  function deliver(event: RealtimeEvent): void {
    const onMessage = subscribe.mock.calls[0][1] as (message: { body: string }) => void;
    onMessage({ body: JSON.stringify(event) });
  }

  it('does not connect until something listens', () => {
    const service = setup('CONTROLLER');

    expect(activate).not.toHaveBeenCalled();

    service.events$.subscribe();

    expect(activate).toHaveBeenCalledTimes(1);
  });

  it('listens where the role is allowed to', () => {
    const controller = setup('CONTROLLER');
    controller.events$.subscribe();
    connected();
    expect(subscribe.mock.calls[0][0]).toBe('/topic/controller/events');

    TestBed.resetTestingModule();

    const technician = setup('TECHNICIAN');
    technician.events$.subscribe();
    connected();
    expect(subscribe.mock.calls[0][0]).toBe('/user/queue/events');
  });

  it('never connects for a role with nothing to hear', () => {
    setup('MANAGER').events$.subscribe();

    expect(activate).not.toHaveBeenCalled();
  });

  it('sends the current token each time it connects', () => {
    const service = setup('TECHNICIAN');
    service.events$.subscribe();
    const stomp = {} as Client;

    (config!.beforeConnect as (client: Client) => void)(stomp);

    expect(stomp.connectHeaders).toEqual({ Authorization: 'Bearer the-token' });
  });

  it('shares one connection, and closes it with the last listener', () => {
    const service = setup('CONTROLLER');
    const first = service.events$.subscribe();
    const second = service.events$.subscribe();
    expect(activate).toHaveBeenCalledTimes(1);

    first.unsubscribe();
    expect(deactivate).not.toHaveBeenCalled();

    second.unsubscribe();
    expect(deactivate).toHaveBeenCalledTimes(1);
  });

  it('hands each listener only the events it asked for', () => {
    const service = setup('CONTROLLER');
    const jobs: RealtimeEvent[] = [];
    const dispatches: RealtimeEvent[] = [];
    service.on('JOB_CHANGED').subscribe((event) => jobs.push(event));
    service.on('DISPATCH_CHANGED').subscribe((event) => dispatches.push(event));
    connected();

    deliver(EVENT);

    expect(jobs).toEqual([EVENT]);
    expect(dispatches).toEqual([]);
  });
});
