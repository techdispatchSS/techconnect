import { Injectable, InjectionToken, inject } from '@angular/core';
import { Client, StompConfig } from '@stomp/stompjs';
import { Observable, filter, share } from 'rxjs';

import { UserRole } from '../auth/auth.models';
import { AuthService } from '../auth/auth.service';

export type RealtimeEventType = 'DISPATCH_CHANGED' | 'JOB_CHANGED';

/** A nudge that something changed. Only ids and a status: screens re-read the data over REST. */
export interface RealtimeEvent {
  readonly type: RealtimeEventType;
  readonly incidentId: string;
  readonly dispatchId: string | null;
  readonly jobId: string | null;
  readonly status: string;
}

const DESTINATIONS: Partial<Record<UserRole, string>> = {
  CONTROLLER: '/topic/controller/events',
  TECHNICIAN: '/user/queue/events',
};

const RECONNECT_DELAY_MS = 5_000;
const HEARTBEAT_MS = 10_000;

/** How the socket gets made, so the specs can stand in for it. */
export const STOMP_CLIENT_FACTORY = new InjectionToken<(config: StompConfig) => Client>(
  'STOMP_CLIENT_FACTORY',
  { providedIn: 'root', factory: () => (config) => new Client(config) },
);

function socketUrl(): string {
  const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
  return `${scheme}://${location.host}/api/ws`;
}

/**
 * Live updates from the backend (#16). There is one connection, opened by the first screen that
 * listens and closed with the last, so a sign-out closes it and the next sign-in opens its own.
 * It reconnects by itself, with whatever token is current by then.
 *
 * <p>This only makes screens quicker: they keep their polling, which covers a dropped socket.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private readonly auth = inject(AuthService);
  private readonly createClient = inject(STOMP_CLIENT_FACTORY);

  readonly events$ = new Observable<RealtimeEvent>((subscriber) => {
    const role = this.auth.role();
    const destination = role && DESTINATIONS[role];
    if (!destination) {
      return;
    }
    const client = this.createClient({
      brokerURL: socketUrl(),
      reconnectDelay: RECONNECT_DELAY_MS,
      heartbeatIncoming: HEARTBEAT_MS,
      heartbeatOutgoing: HEARTBEAT_MS,
      beforeConnect: (stomp) => {
        stomp.connectHeaders = { Authorization: `Bearer ${this.auth.token() ?? ''}` };
      },
      onConnect: () => {
        client.subscribe(destination, (message) =>
          subscriber.next(JSON.parse(message.body) as RealtimeEvent),
        );
      },
    });
    client.activate();
    return () => void client.deactivate();
  }).pipe(share());

  on(...types: RealtimeEventType[]): Observable<RealtimeEvent> {
    return this.events$.pipe(filter((event) => types.includes(event.type)));
  }
}
