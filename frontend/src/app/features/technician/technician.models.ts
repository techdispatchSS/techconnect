/** PRD §8.5 — ON_JOB is set by the system on accepting a dispatch, never by the technician. */
export type TechnicianStatus = 'AVAILABLE' | 'ON_JOB' | 'OFFLINE';

/** `GET/PUT /api/v1/technician/availability` and `PUT /api/v1/technician/location`. */
export interface MyAvailability {
  readonly status: TechnicianStatus;
  /** Holding a job that isn't complete yet; the status can't be changed until it is. */
  readonly onJob: boolean;
  readonly lastLatitude: number | null;
  readonly lastLongitude: number | null;
  readonly lastLocationAt: string | null;
}

export interface Coordinates {
  readonly latitude: number;
  readonly longitude: number;
}
