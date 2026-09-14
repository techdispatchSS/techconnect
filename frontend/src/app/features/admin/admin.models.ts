import { Address, AddressRequest } from '../../core/address.models';
import { TechnicianStatus, UserRole, UserStatus } from '../../core/auth/auth.models';

/** Mirrors the backend `PageResponse` envelope (PRD §8.2). */
export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AdminUser {
  id: string;
  name: string;
  email: string;
  phone: string | null;
  /** Origin point for distance-based job matching (Phase 2). Manager-editable only. Null
   * only for accounts created outside onboarding, e.g. the bootstrap Manager. */
  address: Address | null;
  role: UserRole;
  status: UserStatus;
  /** Present only for technicians. */
  technicianStatus: TechnicianStatus | null;
  /** True while a brute-force lockout is in effect. */
  locked: boolean;
  activatedAt: string | null;
  createdAt: string;
}

export interface CreateUserRequest {
  name: string;
  email: string;
  phone: string | null;
  /** Required — every user onboarded through the portal has a complete address. */
  address: AddressRequest;
  role: UserRole;
}

export interface CreateUserResponse {
  user: AdminUser;
  /** Single-use, expires in 72 hours. Returned so onboarding works without email. */
  activationUrl: string;
}

export interface UpdateUserRequest {
  name: string;
  phone: string | null;
  address: AddressRequest;
  role: UserRole;
}

export interface InviteResponse {
  activationUrl: string;
}

export type AdminAuditAction =
  | 'USER_CREATED'
  | 'USER_UPDATED'
  | 'USER_ROLE_CHANGED'
  | 'USER_DEACTIVATED'
  | 'USER_REACTIVATED'
  | 'INVITE_RESENT'
  | 'SELF_PROFILE_UPDATED';

export interface AuditEntry {
  id: string;
  action: AdminAuditAction;
  actorUserId: string;
  actorName: string | null;
  targetUserId: string | null;
  targetName: string | null;
  /** Raw JSON captured at the time of the change. */
  details: string | null;
  createdAt: string;
}

export interface UserListFilters {
  role: UserRole | null;
  status: UserStatus | null;
  q: string;
  page: number;
  size: number;
}

export interface AuditFilters {
  targetUserId: string | null;
  action: AdminAuditAction | null;
  q: string;
  /** ISO `yyyy-MM-dd`, inclusive at both ends. */
  from: string | null;
  to: string | null;
  page: number;
  size: number;
}

/** Human-readable labels. "Controller" is the PRD's term for what the business calls a dispatcher. */
export const ROLE_LABELS: Record<UserRole, string> = {
  CONTROLLER: 'Controller',
  TECHNICIAN: 'Technician',
  MANAGER: 'Manager',
};

/** Shown on the role-selection cards in the add-user flow. */
export const ROLE_BLURBS: Record<UserRole, string> = {
  TECHNICIAN: 'Mobile app only — receives job broadcasts, updates job status, captures evidence.',
  CONTROLLER: 'Dispatch dashboard — creates and broadcasts jobs, reviews and closes submissions.',
  MANAGER: 'Full admin — dashboards, reporting and user management.',
};

export const STATUS_LABELS: Record<UserStatus, string> = {
  PENDING_ACTIVATION: 'Pending activation',
  ACTIVE: 'Active',
  DISABLED: 'Deactivated',
};

export const AUDIT_ACTION_LABELS: Record<AdminAuditAction, string> = {
  USER_CREATED: 'User created',
  USER_UPDATED: 'Details updated',
  USER_ROLE_CHANGED: 'Role changed',
  USER_DEACTIVATED: 'Deactivated',
  USER_REACTIVATED: 'Reactivated',
  INVITE_RESENT: 'Invite resent',
  SELF_PROFILE_UPDATED: 'Updated their own profile',
};
