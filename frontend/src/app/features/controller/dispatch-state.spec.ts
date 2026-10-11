import { DispatchSummary } from './controller.models';
import { canResend, dispatchState, needsNewDispatch, timeLeft } from './dispatch-state';

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

describe('dispatchState', () => {
  it('says nothing has been sent when there is no dispatch', () => {
    const state = dispatchState(null);

    expect(state.kind).toBe('none');
    expect(state.headline).toBe('Not dispatched yet');
  });

  it('says how many it was sent to and that it is awaiting confirmation', () => {
    const state = dispatchState(summary());

    expect(state.kind).toBe('waiting');
    expect(state.headline).toBe('Sent to 3 technicians — awaiting confirmation');
    expect(state.chip).toBe('Sent to 3 · awaiting');
    expect(state.detail).toBe('3 yet to answer');
  });

  it('counts declines against who is still to answer', () => {
    const state = dispatchState(summary({ pending: 2, declined: 1 }));

    expect(state.detail).toBe('1 declined · 2 yet to answer');
  });

  it('uses the singular for one technician', () => {
    const state = dispatchState(summary({ invited: 1, pending: 1 }));

    expect(state.headline).toBe('Sent to 1 technician — awaiting confirmation');
  });

  it('names whoever accepted', () => {
    const state = dispatchState(
      summary({ status: 'ACCEPTED', pending: 0, noResponse: 2, acceptedTechnicianName: 'Thabo M' }),
    );

    expect(state.kind).toBe('accepted');
    expect(state.headline).toBe('Accepted by Thabo M');
    expect(state.chip).toBe('Accepted · Thabo M');
  });

  it('tells a lapsed window from everyone declining', () => {
    const lapsed = dispatchState(summary({ status: 'EXPIRED', pending: 0, noResponse: 3 }));
    const declined = dispatchState(summary({ status: 'EXPIRED', pending: 0, declined: 3 }));

    expect(lapsed.kind).toBe('expired');
    expect(declined.kind).toBe('declined');
    expect(declined.headline).toBe('Every technician declined');
  });
});

describe('what the Controller can do next', () => {
  it('creates a dispatch when none is out, or the last one is dead', () => {
    expect(needsNewDispatch(null)).toBe(true);
    expect(needsNewDispatch(summary({ status: 'EXPIRED', pending: 0, noResponse: 3 }))).toBe(true);
    expect(needsNewDispatch(summary({ status: 'EXPIRED', pending: 0, declined: 3 }))).toBe(true);
  });

  it('follows a dispatch that is still out or has been taken', () => {
    expect(needsNewDispatch(summary())).toBe(false);
    expect(needsNewDispatch(summary({ status: 'ACCEPTED', pending: 0 }))).toBe(false);
  });

  it('offers a resend only while someone is yet to answer inside the window', () => {
    expect(canResend(summary())).toBe(true);
    expect(canResend(summary({ pending: 0, declined: 3 }))).toBe(false);
    expect(canResend(summary({ status: 'ACCEPTED', pending: 0 }))).toBe(false);
    expect(canResend(summary({ status: 'EXPIRED' }))).toBe(false);
    expect(canResend(summary({ expiresAt: new Date(Date.now() - 1000).toISOString() }))).toBe(
      false,
    );
    expect(canResend(null)).toBe(false);
  });

  it('shows the time left only for a dispatch inside its window', () => {
    expect(timeLeft(summary())).toBe('7 min left');
    expect(timeLeft(summary({ status: 'ACCEPTED' }))).toBeNull();
    expect(timeLeft(null)).toBeNull();
  });
});
