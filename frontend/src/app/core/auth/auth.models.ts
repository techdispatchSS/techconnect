/** Mirrors the backend `UserRole` enum. Manager holds the admin rights (PRD FR-01). */
export type UserRole = 'CONTROLLER' | 'TECHNICIAN' | 'MANAGER';

export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED';

export type TechnicianStatus = 'AVAILABLE' | 'ON_JOB' | 'OFFLINE';

/** PRD §8.1 — the exact shape returned by `POST /api/v1/auth/login`. */
export interface LoginResponse {
  token: string;
  role: UserRole;
  userId: string;
  name: string;
}

export interface AuthSession {
  token: string;
  userId: string;
  name: string;
  role: UserRole;
  /** Epoch milliseconds, read from the token's `exp` claim. */
  expiresAt: number;
}
