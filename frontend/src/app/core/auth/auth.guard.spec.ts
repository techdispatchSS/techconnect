import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';

import { authGuard, roleGuard } from './auth.guard';
import { UserRole } from './auth.models';
import { AuthService } from './auth.service';

/** Minimal stand-in exposing only what the guards read. */
class AuthServiceStub {
  readonly authenticated = signal(false);
  readonly currentRole = signal<UserRole | null>(null);

  isAuthenticated = () => this.authenticated();
  role = () => this.currentRole();
  hasRole = (...roles: UserRole[]) => {
    const role = this.currentRole();
    return role !== null && roles.includes(role);
  };
}

describe('auth guards', () => {
  let auth: AuthServiceStub;

  beforeEach(() => {
    auth = new AuthServiceStub();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: AuthService, useValue: auth },
      ],
    });
  });

  function runAuthGuard(url: string): boolean | UrlTree {
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(
      () => authGuard({} as ActivatedRouteSnapshot, state) as boolean | UrlTree,
    );
  }

  function runRoleGuard(roles: UserRole[]): boolean | UrlTree {
    const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(
      () => roleGuard(route, {} as RouterStateSnapshot) as boolean | UrlTree,
    );
  }

  it('lets an authenticated user through', () => {
    auth.authenticated.set(true);
    expect(runAuthGuard('/admin/users')).toBe(true);
  });

  it('sends an anonymous user to login, preserving where they were headed', () => {
    const result = runAuthGuard('/admin/users');
    const router = TestBed.inject(Router);

    expect(result).toBeInstanceOf(UrlTree);
    // The redirect must carry the original destination, or the user lands on the wrong page
    // after signing in.
    expect(router.serializeUrl(result as UrlTree)).toContain('redirectTo=%2Fadmin%2Fusers');
  });

  it('allows a manager into a manager-only route', () => {
    auth.authenticated.set(true);
    auth.currentRole.set('MANAGER');
    expect(runRoleGuard(['MANAGER'])).toBe(true);
  });

  it('diverts a technician away from a manager-only route', () => {
    auth.authenticated.set(true);
    auth.currentRole.set('TECHNICIAN');

    const result = runRoleGuard(['MANAGER']);
    const router = TestBed.inject(Router);

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/forbidden');
  });
});
