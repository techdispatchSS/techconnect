import { IncidentPriority, JobStatus } from './controller.models';

/** How long ago, as the queue says it: "12m", "3h", "2h 10m", "2d 4h". */
export function elapsedLabel(since: string | null, now = Date.now()): string {
  if (!since) {
    return '—';
  }
  const minutes = Math.max(0, Math.round((now - new Date(since).getTime()) / 60_000));
  if (minutes < 60) {
    return `${minutes}m`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    const rest = minutes % 60;
    return rest === 0 ? `${hours}h` : `${hours}h ${rest}m`;
  }
  const days = Math.floor(hours / 24);
  const restHours = hours % 24;
  return restHours === 0 ? `${days}d` : `${days}d ${restHours}h`;
}

/** The SLA as a countdown: "Overdue by 20m" or "3h 55m left". Null when there is no SLA. */
export function slaCountdown(
  slaDueAt: string | null,
  closed: boolean,
  now = Date.now(),
): { text: string; overdue: boolean } | null {
  if (!slaDueAt || closed) {
    return null;
  }
  const delta = new Date(slaDueAt).getTime() - now;
  const minutes = Math.round(Math.abs(delta) / 60_000);
  const text =
    minutes < 60 ? `${minutes}m` : elapsedLabel(new Date(now - Math.abs(delta)).toISOString(), now);
  return delta < 0
    ? { text: `Overdue by ${text}`, overdue: true }
    : { text: `${text} left`, overdue: false };
}

/** The modifier a status pill carries, so colour says "moving", "stuck" or "done". */
export function statusTone(status: JobStatus): 'moving' | 'held' | 'review' | 'done' {
  switch (status) {
    case 'ON_HOLD':
      return 'held';
    case 'COMPLETE':
    case 'SUBMITTED':
      return 'review';
    case 'CLOSED':
      return 'done';
    default:
      return 'moving';
  }
}

export function priorityTone(priority: IncidentPriority | null): string {
  return priority ? `controller-pill controller-pill--${priority.toLowerCase()}` : '';
}

/** Where the technician is on the way to done, as a 0–1 fraction, for the progress bar. */
export function progressOf(status: JobStatus): number {
  switch (status) {
    case 'EN_ROUTE':
      return 0.15;
    case 'ON_SITE':
      return 0.35;
    case 'WORK_STARTED':
      return 0.55;
    case 'ON_HOLD':
      return 0.55;
    case 'WORK_RESUMED':
      return 0.7;
    case 'COMPLETE':
      return 0.85;
    case 'SUBMITTED':
      return 0.95;
    case 'CLOSED':
      return 1;
  }
}
