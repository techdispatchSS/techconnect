import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { UserRole } from './auth.models';

/** The signed-in user's own record, as returned by `GET /auth/me`. */
export interface Profile {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  address: string | null;
  role: UserRole;
  /**
   * True for Managers only. A technician's address is the origin point for distance-based
   * job matching, so changing it is a dispatch decision that stays with the Manager.
   */
  canEditAddress: boolean;
}

export interface UpdateProfileRequest {
  name: string;
  phone: string | null;
  address: string | null;
}

/** Account lifecycle calls that do not themselves establish a session. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);

  /**
   * Confirms the stored token still corresponds to a live, active account. Returns 401 for a
   * session the backend has revoked, which the auth interceptor turns into a sign-out.
   */
  me(): Observable<Profile> {
    return this.http.get<Profile>('v1/auth/me');
  }

  /** Email and role are absent by design — both are administrative, audited changes. */
  updateProfile(request: UpdateProfileRequest): Observable<Profile> {
    return this.http.put<Profile>('v1/auth/me', request);
  }

  /** Redeems an invite link and sets the user's first password. */
  activate(token: string, password: string): Observable<void> {
    return this.http.post<void>('v1/auth/activate', { token, password });
  }

  /** Resolves identically for unknown addresses — the backend never reveals which exist. */
  forgotPassword(email: string): Observable<void> {
    return this.http.post<void>('v1/auth/forgot-password', { email });
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>('v1/auth/reset-password', { token, password });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('v1/auth/change-password', { currentPassword, newPassword });
  }
}
