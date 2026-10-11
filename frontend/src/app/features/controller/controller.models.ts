/** Mirrors the backend `IncidentPriority`/`IncidentStatus`/`DispatchType` enums (FR-02–FR-04). */
export type IncidentPriority = 'HIGH' | 'MEDIUM' | 'LOW';
export type IncidentStatus = 'NEW' | 'IN_PROGRESS' | 'ON_HOLD' | 'OVERDUE' | 'CLOSED';
export type DispatchType = 'BROADCAST' | 'ASSIGN';
export type DispatchLifecycleStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED';
export type DispatchResponseStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'NO_RESPONSE';
export type TechnicianAvailability = 'AVAILABLE' | 'ON_JOB' | 'OFFLINE';

/** PRD §8.2's pagination envelope — redefined per feature rather than shared, matching
 * `admin.models.ts`'s own `PageResponse<T>`. */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Incident {
  id: string;
  ticketRef: string;
  clientName: string;
  siteAddress: string | null;
  siteLatitude: number | null;
  siteLongitude: number | null;
  issueType: string | null;
  priority: IncidentPriority | null;
  sla: string | null;
  status: IncidentStatus;
  overdue: boolean;
  contactName: string | null;
  contactPhone: string | null;
  description: string | null;
  additionalNotes: string | null;
  slaDueAt: string | null;
  createdAt: string;
  requiredSkills: string[];
  /** True once any dispatch has been created — what decides "Create dispatch" versus "View
   * progress", since an OVERDUE (or any other) status doesn't say either way. */
  hasDispatch: boolean;
  /** Where the latest dispatch stands — who it went to and who has answered. Null if none sent. */
  dispatch: DispatchSummary | null;
}

/** Mirrors the backend `DispatchSummary`: the state a ticket shows after a dispatch goes out. */
export interface DispatchSummary {
  id: string;
  type: DispatchType;
  status: DispatchLifecycleStatus;
  sentAt: string;
  expiresAt: string;
  /** Technicians it was sent to. */
  invited: number;
  /** Of those, how many have not answered. */
  pending: number;
  declined: number;
  /** Never answered before it lapsed, or the job went to someone else first. */
  noResponse: number;
  acceptedTechnicianName: string | null;
  /** Times technicians have been notified, the first send included. */
  notificationCount: number;
  lastNotifiedAt: string | null;
}

export interface ResendResult {
  notified: number;
  notificationCount: number;
  lastNotifiedAt: string;
}

export interface IncidentKpis {
  newCount: number;
  unassignedCount: number;
  inProgressCount: number;
  overdueCount: number;
}

export interface IncidentListFilters {
  status?: IncidentStatus;
  priority?: IncidentPriority;
  unassigned?: boolean;
  q: string;
  page: number;
  size: number;
}

export interface TechnicianCandidate {
  id: string;
  name: string;
  score: number;
  distanceKm: number | null;
  latitude: number | null;
  longitude: number | null;
  matchedSkills: number;
  totalRequiredSkills: number;
  completedJobCount: number;
  status: TechnicianAvailability;
}

export interface CreateDispatchRequest {
  incidentId: string;
  dispatchType: DispatchType;
  jobType: string | null;
  requiredSkills: string[];
  requiredCertifications: string[];
  slaResponse: string | null;
  siteContact: string | null;
  notesForTechnician: string | null;
  technicianIds: string[];
}

export interface DispatchCreateResponse {
  id: string;
  incidentId: string;
  dispatchType: DispatchType;
  status: DispatchLifecycleStatus;
  expiresAt: string;
  invitedTechnicianIds: string[];
}

export interface DispatchResponseEntry {
  technicianId: string;
  technicianName: string;
  response: DispatchResponseStatus;
  respondedAt: string | null;
}

/**
 * The ticket progress view: the incident, the most recent dispatch sent for it, and how every
 * invited technician has responded so far. There is deliberately no on-site job timeline (en
 * route / on site / work started) here — the technician app that would write those events
 * doesn't exist yet, so there's nothing real to show beyond dispatch and response state.
 */
export interface IncidentProgress {
  incident: Incident;
  dispatchId: string;
  dispatchType: DispatchType;
  dispatchStatus: DispatchLifecycleStatus;
  jobType: string | null;
  requiredSkills: string[];
  requiredCertifications: string[];
  slaResponse: string | null;
  siteContact: string | null;
  notesForTechnician: string | null;
  dispatchCreatedAt: string;
  dispatchExpiresAt: string;
  responses: DispatchResponseEntry[];
  acceptedTechnicianName: string | null;
}

/** An incident the Controller logs by hand (a phone call). Mirrors `CreateIncidentRequest`. */
export interface CreateIncidentRequest {
  clientName: string;
  siteAddress: string | null;
  siteLatitude: number | null;
  siteLongitude: number | null;
  contactName: string | null;
  contactPhone: string | null;
  issueType: string | null;
  priority: IncidentPriority;
  /** Response target in hours; the server derives one from the priority when omitted. */
  slaHours: number | null;
  description: string | null;
  additionalNotes: string | null;
}

/** How the Freshdesk connection stands. `simulation` also gates the demo-only controls. */
export interface FreshdeskStatus {
  mode: 'SIMULATED' | 'NOT_CONNECTED';
  simulation: boolean;
  lastSyncAt: string | null;
  lastSuccessAt: string | null;
  lastError: string | null;
}

export interface SimulatedTicket {
  ticketId: string;
  created: number;
  updated: number;
}

export interface DispatchAnswer {
  dispatchId: string;
  outcome: 'ACCEPTED' | 'DECLINED';
}

/** The queue's filter tabs — "Unassigned" and the KPI counts share one source of truth
 * (`IncidentKpis`), matching how the design mockup reuses the same numbers for both. */
export const QUEUE_TABS = ['New', 'Unassigned', 'In progress', 'Overdue'] as const;
export type QueueTab = (typeof QUEUE_TABS)[number];

export const PRIORITY_LABELS: Record<IncidentPriority, string> = {
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};
