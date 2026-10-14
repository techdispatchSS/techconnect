import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { Address, AddressRequest } from '../address.models';
import { UserRole } from './auth.models';

/** The signed-in user's own record, as returned by `GET /auth/me`. */
export interface Profile {
  id: string;
  email: string;
  name: string;
  phone: string | null;
  address: Address | null;
  role: UserRole;
  /**
   * True for Managers only. A technician's address is the origin point for distance-based
   * job matching, so changing it is a dispatch decision that stays with the Manager.
   */
  canEditAddress: boolean;
  /** The IANA zone this person was last seen in; null until they first sign in. */
  timeZone: string | null;
}

export interface UpdateProfileRequest {
  name: string;
  phone: string | null;
  /** Omit entirely for a role other than MANAGER — the backend ignores it either way, but a
   * Controller or Technician has no complete address to submit just to change their phone. */
  address?: AddressRequest;
}

/**
 * Where this browser says it is, as an IANA zone id; null if it will not say. A token so a test
 * can place the browser somewhere without replacing the whole of `Intl`.
 */
export const BROWSER_TIME_ZONE = new InjectionToken<() => string | null>('BROWSER_TIME_ZONE', {
  providedIn: 'root',
  factory: () => () => {
    try {
      return Intl.DateTimeFormat().resolvedOptions().timeZone || null;
    } catch {
      return null;
    }
  },
});

/** Account lifecycle calls that do not themselves establish a session. */
@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly http = inject(HttpClient);
  private readonly browserTimeZone = inject(BROWSER_TIME_ZONE);

  /**
   * Confirms the stored token still corresponds to a live, active account. Returns 401 for a
   * session the backend has revoked, which the auth interceptor turns into a sign-out.
   */
  me(): Observable<Profile> {
    return this.http
      .get<Profile>('v1/auth/me')
      .pipe(tap((profile) => this.reportTimeZone(profile)));
  }

  /**
   * Tells the backend which time zone this browser is in, so a time in an email is said in the
   * person's own ("respond by 21:54 EAT" in Nairobi). Done on every session check but only sent
   * when it differs from what is on record, so it follows someone who travels without chattering.
   * Best effort: a failure here must never get in the way of using the app.
   */
  private reportTimeZone(profile: Profile): void {
    const zone = this.browserTimeZone();
    if (!zone || zone === profile.timeZone) {
      return;
    }
    this.http
      .put<Profile>('v1/auth/me/time-zone', { timeZone: zone })
      .subscribe({ error: () => undefined });
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
    return this.http.post<void>('v1/auth/forgot-password', { email: email.trim() });
  }

  resetPassword(token: string, password: string): Observable<void> {
    return this.http.post<void>('v1/auth/reset-password', { token, password });
  }

  changePassword(currentPassword: string, newPassword: string): Observable<void> {
    return this.http.post<void>('v1/auth/change-password', { currentPassword, newPassword });
  }
}
