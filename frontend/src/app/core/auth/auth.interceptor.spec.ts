import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { authInterceptor } from './auth.interceptor';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;
  let logout: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    logout = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { token: () => 'test-token', logout } },
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('attaches the bearer token to protected requests', () => {
    http.get('/api/v1/admin/users').subscribe();

    const request = controller.expectOne('/api/v1/admin/users');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({});
  });

  it('does not attach a token to public auth endpoints', () => {
    http.post('/api/v1/auth/login', {}).subscribe();

    const request = controller.expectOne('/api/v1/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('ends the session when a protected request comes back 401', () => {
    // The backend revokes tokens on offboarding rather than waiting for expiry, so this can
    // happen at any time on a token the client still believes is valid.
    http.get('/api/v1/admin/users').subscribe({ error: () => undefined });

    controller
      .expectOne('/api/v1/admin/users')
      .flush({ error: 'Unauthorized' }, { status: 401, statusText: 'Unauthorized' });

    expect(logout).toHaveBeenCalled();
  });

  it('leaves the session alone when a login attempt is rejected', () => {
    // A 401 here means "wrong password", not "your session died".
    http.post('/api/v1/auth/login', {}).subscribe({ error: () => undefined });

    controller
      .expectOne('/api/v1/auth/login')
      .flush({ error: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });

    expect(logout).not.toHaveBeenCalled();
  });
});
