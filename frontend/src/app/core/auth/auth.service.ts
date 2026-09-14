import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { AuthSession, LoginResponse, UserRole } from './auth.models';

const STORAGE_KEY = 'techconnect.session';

/**
 * Holds the signed-in session.
 *
 * The JWT is the single source of truth for identity: the role and expiry are read from
 * its claims rather than tracked separately, so a tampered stored entry cannot grant a role
 * the server will not honour — the backend re-checks every request anyway.
 *
 * <p>"Keep me signed in" (login's `remember` flag) chooses where that entry lives:
 * `localStorage` survives closing the browser, `sessionStorage` does not. Only one copy is
 * ever kept, so switching between the two on a later login can't leave a stale duplicate
 * that {@link restoreSession} might resurrect after the "forgotten" one should have expired.
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

  login(email: string, password: string, remember = true): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('v1/auth/login', { email, password })
      .pipe(tap((response) => this.store(response, remember)));
  }

  /** Clears local state. There is no server call: the token is stateless by design. */
  logout(redirect = true): void {
    this.session.set(null);
    localStorage.removeItem(STORAGE_KEY);
    sessionStorage.removeItem(STORAGE_KEY);
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

  private store(response: LoginResponse, remember: boolean): void {
    const session: AuthSession = {
      token: response.token,
      userId: response.userId,
      name: response.name,
      role: response.role,
      expiresAt: expiryOf(response.token),
    };
    this.session.set(session);

    const [target, other] = remember
      ? [localStorage, sessionStorage]
      : [sessionStorage, localStorage];
    target.setItem(STORAGE_KEY, JSON.stringify(session));
    other.removeItem(STORAGE_KEY);
  }
}

/**
 * Reads a stored session, discarding one whose token has already expired. Without the
 * expiry check the app would render its authenticated shell and only discover the session
 * was dead when the first request came back 401.
 *
 * <p>Checks `localStorage` first, then `sessionStorage` — a signed-in tab always has at most
 * one of the two populated (see {@link AuthService.store}), so the order only matters for
 * which one a stale, already-expired leftover in the other is read from.
 */
function restoreSession(): AuthSession | null {
  return readSession(localStorage) ?? readSession(sessionStorage);
}

function readSession(storage: Storage): AuthSession | null {
  const raw = storage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }

  try {
    const session = JSON.parse(raw) as AuthSession;
    if (!session.token || session.expiresAt <= Date.now()) {
      storage.removeItem(STORAGE_KEY);
      return null;
    }
    return session;
  } catch {
    storage.removeItem(STORAGE_KEY);
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
