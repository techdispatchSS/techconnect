/** Mirrors the backend `IncidentPriority`/`IncidentStatus`/`DispatchType` enums (FR-02–FR-04). */
export type IncidentPriority = 'HIGH' | 'MEDIUM' | 'LOW';
export type IncidentStatus = 'NEW' | 'IN_PROGRESS' | 'ON_HOLD' | 'OVERDUE' | 'CLOSED';
export type DispatchType = 'BROADCAST' | 'ASSIGN';
export type DispatchLifecycleStatus = 'PENDING' | 'ACCEPTED' | 'EXPIRED';
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

/** The queue's filter tabs — "Unassigned" and the KPI counts share one source of truth
 * (`IncidentKpis`), matching how the design mockup reuses the same numbers for both. */
export const QUEUE_TABS = ['New', 'Unassigned', 'In progress', 'Overdue'] as const;
export type QueueTab = (typeof QUEUE_TABS)[number];

export const PRIORITY_LABELS: Record<IncidentPriority, string> = {
  HIGH: 'High',
  MEDIUM: 'Medium',
  LOW: 'Low',
};
