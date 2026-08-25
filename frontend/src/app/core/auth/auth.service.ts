import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AuthSession, LoginResponse, UserRole } from './auth.models';

const STORAGE_KEY = 'techdispatch.session';

/**
 * Holds the signed-in session.
 *
 * The JWT is the single source of truth for identity: the role and expiry are read from
 * its claims rather than tracked separately, so a tampered `localStorage` entry cannot
 * grant a role the server will not honour — the backend re-checks every request anyway.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly session = signal<AuthSession | null>(restoreSession());

  readonly currentUser = this.session.asReadonly();
  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly role = computed<UserRole | null>(() => this.session()?.role ?? null);
  readonly displayName = computed(() => this.session()?.name ?? '');

  login(email: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('v1/auth/login', { email, password })
      .pipe(tap((response) => this.store(response)));
  }

  /** Clears local state. There is no server call: the token is stateless by design. */
  logout(redirect = true): void {
    this.session.set(null);
    localStorage.removeItem(STORAGE_KEY);
    if (redirect) {
      void this.router.navigate(['/login']);
    }
  }

  token(): string | null {
    return this.session()?.token ?? null;
  }

  hasRole(...roles: UserRole[]): boolean {
    const role = this.role();
    return role !== null && roles.includes(role);
  }

  /** Where a user lands after signing in, and what the root path redirects to. */
  homeRouteFor(role: UserRole | null): string {
    switch (role) {
      case 'MANAGER':
        return '/admin/users';
      case 'CONTROLLER':
        return '/incidents';
      case 'TECHNICIAN':
        return '/jobs';
      default:
        return '/login';
    }
  }

  private store(response: LoginResponse): void {
    const session: AuthSession = {
      token: response.token,
      userId: response.userId,
      name: response.name,
      role: response.role,
      expiresAt: expiryOf(response.token),
    };
    this.session.set(session);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  }
}

/**
 * Reads a stored session, discarding one whose token has already expired. Without the
 * expiry check the app would render its authenticated shell and only discover the session
 * was dead when the first request came back 401.
 */
function restoreSession(): AuthSession | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const session = JSON.parse(raw) as AuthSession;
    if (!session.token || session.expiresAt <= Date.now()) {
      localStorage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    localStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

/** Extracts `exp` (seconds) from a JWT payload and converts it to epoch milliseconds. */
function expiryOf(token: string): number {
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    const claims = JSON.parse(json) as { exp?: number };
    return claims.exp ? claims.exp * 1000 : 0;
  } catch {
    return 0;
  }
}
