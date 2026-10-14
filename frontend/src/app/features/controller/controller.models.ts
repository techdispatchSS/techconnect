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
 * invited technician has responded so far. Once accepted it names the job, whose own page has
 * the on-site timeline.
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
  /** The job opened when it was accepted, and where it stands; null before that. */
  jobId: string | null;
  jobStatus: JobStatus | null;
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

/** Mirrors the backend `JobStatus` (FR-05–FR-07). */
export type JobStatus =
  | 'EN_ROUTE'
  | 'ON_SITE'
  | 'WORK_STARTED'
  | 'ON_HOLD'
  | 'WORK_RESUMED'
  | 'COMPLETE'
  | 'SUBMITTED'
  | 'CLOSED';

/** A tab on the job screens: what is being worked, what awaits sign-off, what is finished. */
export type JobStage = 'ACTIVE' | 'REVIEW' | 'CLOSED';

export const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  EN_ROUTE: 'En route',
  ON_SITE: 'On site',
  WORK_STARTED: 'Work started',
  ON_HOLD: 'On hold',
  WORK_RESUMED: 'Work resumed',
  COMPLETE: 'Work complete',
  SUBMITTED: 'Report submitted',
  CLOSED: 'Closed',
};

export interface JobSummary {
  id: string;
  incidentId: string;
  ticketRef: string | null;
  clientName: string | null;
  siteAddress: string | null;
  priority: IncidentPriority | null;
  slaDueAt: string | null;
  overdue: boolean;
  technicianId: string;
  technicianName: string;
  status: JobStatus;
  acceptedAt: string;
  arrivedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  submittedAt: string | null;
  closedAt: string | null;
}

export interface JobHistoryEntry {
  status: JobStatus;
  previousStatus: JobStatus | null;
  reason: string | null;
  changedById: string;
  changedByName: string;
  changedAt: string;
}

export interface JobDetail {
  job: JobSummary;
  contactName: string | null;
  contactPhone: string | null;
  issueType: string | null;
  description: string | null;
  jobType: string | null;
  notesForTechnician: string | null;
  resolutionNotes: string | null;
  rootCause: string | null;
  recommendations: string | null;
  labourHours: number | null;
  parts: { name: string; quantity: number; serialNumber: string | null }[];
  closingNotes: string | null;
  closedByName: string | null;
  /** Oldest first. */
  history: JobHistoryEntry[];
  canClose: boolean;
}

export interface JobCounts {
  active: number;
  review: number;
  closed: number;
}

export interface TechnicianRosterEntry {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  accountStatus: 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED';
  availability: TechnicianAvailability;
  city: string | null;
  region: string | null;
  country: string | null;
  skills: string[];
  currentJob: {
    jobId: string;
    ticketRef: string | null;
    clientName: string | null;
    status: JobStatus;
  } | null;
  jobsClosed: number;
  lastSeenAt: string | null;
}

/** Mirrors the backend `ReportResponse`. A measure with nothing behind it is null, never 0. */
export interface Report {
  from: string;
  to: string;
  zone: string;
  incidents: { created: number; closed: number; open: number; overdueNow: number };
  sla: { withinSla: number; late: number; compliancePercent: number | null };
  dispatching: {
    dispatches: number;
    accepted: number;
    expired: number;
    invitations: number;
    accepts: number;
    declines: number;
    noResponses: number;
    averageMinutesToAccept: number | null;
    acceptRatePercent: number | null;
  };
  jobs: {
    opened: number;
    submitted: number;
    closed: number;
    averageHoursOnSite: number | null;
    averageLabourHours: number | null;
    averageHoursToClose: number | null;
  };
  byPriority: {
    priority: IncidentPriority;
    created: number;
    closed: number;
    withinSla: number;
    late: number;
  }[];
  byTechnician: {
    technicianId: string;
    name: string;
    invited: number;
    accepted: number;
    declined: number;
    noResponse: number;
    jobs: number;
    jobsClosed: number;
    averageLabourHours: number | null;
  }[];
  daily: { date: string; created: number; closed: number }[];
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
