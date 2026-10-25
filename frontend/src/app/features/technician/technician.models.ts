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

export type DispatchType = 'BROADCAST' | 'ASSIGN';
export type IncidentPriority = 'HIGH' | 'MEDIUM' | 'LOW';

/** `GET /api/v1/technician/dispatches` — a job offered to me, waiting on my answer (FR-04). */
export interface DispatchOffer {
  readonly dispatchId: string;
  /** BROADCAST went to several technicians: the first to accept gets it. */
  readonly dispatchType: DispatchType;
  readonly expiresAt: string;
  readonly ticketRef: string | null;
  readonly clientName: string;
  readonly siteAddress: string | null;
  readonly priority: IncidentPriority | null;
  readonly sla: string | null;
  readonly issueType: string | null;
  readonly description: string | null;
  readonly jobType: string | null;
  readonly requiredSkills: string | null;
  readonly notesForTechnician: string | null;
  /** From my last shared location; null if either end has no position. */
  readonly distanceKm: number | null;
}

export interface DispatchAnswer {
  readonly dispatchId: string;
  readonly outcome: 'ACCEPTED' | 'DECLINED';
}
