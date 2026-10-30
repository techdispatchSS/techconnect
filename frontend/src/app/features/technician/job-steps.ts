import { JobStatus } from '../controller/controller.models';

/** What the technician taps to move a job to each status, in their words rather than ours. */
export const STEP_LABELS: Partial<Record<JobStatus, string>> = {
  ON_SITE: "I've arrived",
  WORK_STARTED: 'Start work',
  ON_HOLD: 'Put on hold',
  WORK_RESUMED: 'Resume work',
  COMPLETE: 'Mark work complete',
};

/** Steps that can't be taken back, so the app asks once more before sending them. */
export const CONFIRM_STEPS: ReadonlySet<JobStatus> = new Set<JobStatus>(['COMPLETE']);

/** Statuses in which the job is still the technician's to work: not yet reported on. */
export const IN_HAND: ReadonlySet<JobStatus> = new Set<JobStatus>([
  'EN_ROUTE',
  'ON_SITE',
  'WORK_STARTED',
  'ON_HOLD',
  'WORK_RESUMED',
  'COMPLETE',
]);

/**
 * The server's allowed steps, the one that moves the job forward first: going on hold is a
 * detour, so it never takes the main button while there is another way on.
 */
export function orderSteps(steps: readonly JobStatus[]): JobStatus[] {
  return [...steps].sort((a, b) => Number(a === 'ON_HOLD') - Number(b === 'ON_HOLD'));
}

/** A directions link for the site, opened in the phone's maps app. */
export function directionsUrl(address: string | null): string | null {
  return address
    ? `https://www.google.com/maps/dir/?api=1&destination=${encodeURIComponent(address)}`
    : null;
}
