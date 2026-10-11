import { DispatchSummary, IncidentStatus } from './controller.models';

/** How a ticket's dispatch reads to a Controller, derived from the server's summary. */
export type DispatchStateKind =
  /** Nothing has been sent. */
  | 'none'
  /** Sent, and nobody has taken it yet. */
  | 'waiting'
  /** A technician accepted. */
  | 'accepted'
  /** The window passed with nobody taking it. */
  | 'expired'
  /** Everyone it was sent to said no. */
  | 'declined';

export interface DispatchState {
  readonly kind: DispatchStateKind;
  /** The short chip text for a queue row. */
  readonly chip: string;
  /** The full sentence for the ticket: "Sent to 3 technicians — awaiting confirmation". */
  readonly headline: string;
  /** The breakdown under it, or null when there is nothing to add. */
  readonly detail: string | null;
}

function technicians(count: number): string {
  return `${count} technician${count === 1 ? '' : 's'}`;
}

/**
 * Turns the server's dispatch summary into what the Controller should read. Kept pure and
 * separate from the components so the wording is one place, and testable without a DOM.
 */
export function dispatchState(summary: DispatchSummary | null): DispatchState {
  if (!summary) {
    return { kind: 'none', chip: 'Not dispatched', headline: 'Not dispatched yet', detail: null };
  }

  const sent = `Sent to ${technicians(summary.invited)}`;

  switch (summary.status) {
    case 'ACCEPTED':
      return {
        kind: 'accepted',
        chip: summary.acceptedTechnicianName
          ? `Accepted · ${summary.acceptedTechnicianName}`
          : 'Accepted',
        headline: summary.acceptedTechnicianName
          ? `Accepted by ${summary.acceptedTechnicianName}`
          : 'Accepted by a technician',
        detail: `${sent}.`,
      };

    case 'EXPIRED': {
      const everyoneDeclined = summary.invited > 0 && summary.declined === summary.invited;
      return everyoneDeclined
        ? {
            kind: 'declined',
            chip: 'All declined',
            headline: 'Every technician declined',
            detail: `${sent}. Choose different technicians to try again.`,
          }
        : {
            kind: 'expired',
            chip: 'No answer in time',
            headline: 'Nobody accepted in time',
            detail: `${sent}. The response window passed.`,
          };
    }

    default: {
      const parts: string[] = [];
      if (summary.declined > 0) {
        parts.push(`${summary.declined} declined`);
      }
      parts.push(`${summary.pending} yet to answer`);
      return {
        kind: 'waiting',
        chip: `Sent to ${summary.invited} · awaiting`,
        headline: `${sent} — awaiting confirmation`,
        detail: parts.join(' · '),
      };
    }
  }
}

/** Whether the primary action should create a new dispatch rather than open the existing one. */
export function needsNewDispatch(summary: DispatchSummary | null): boolean {
  const kind = dispatchState(summary).kind;
  return kind === 'none' || kind === 'expired' || kind === 'declined';
}

/** Whether a notification can still usefully be resent: sent, unanswered, and not yet lapsed. */
export function canResend(summary: DispatchSummary | null): boolean {
  return (
    dispatchState(summary).kind === 'waiting' &&
    summary !== null &&
    summary.pending > 0 &&
    new Date(summary.expiresAt).getTime() > Date.now()
  );
}

/** "7m left" / "under a minute left" for a dispatch still inside its window; null otherwise. */
export function timeLeft(summary: DispatchSummary | null): string | null {
  if (!summary || summary.status !== 'PENDING') {
    return null;
  }
  const ms = new Date(summary.expiresAt).getTime() - Date.now();
  if (ms <= 0) {
    return 'window closing';
  }
  const minutes = Math.ceil(ms / 60_000);
  return minutes <= 1 ? 'under a minute left' : `${minutes} min left`;
}

export const INCIDENT_STATUS_LABELS: Record<IncidentStatus, string> = {
  NEW: 'New',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  OVERDUE: 'Overdue',
  CLOSED: 'Closed',
};
