import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { LoginResponse } from '../../../core/auth/auth.models';
import { AuthService } from '../../../core/auth/auth.service';
import { Login } from './login';

const MANAGER_RESPONSE: LoginResponse = {
  token: 't',
  role: 'MANAGER',
  userId: 'u1',
  name: 'Test Manager',
};

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let el: HTMLElement;
  let login: ReturnType<typeof vi.fn>;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function setup(loginImpl: () => Observable<LoginResponse>): Promise<void> {
    login = vi.fn(loginImpl);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            login,
            homeRouteFor: (role: string | null) => (role === 'MANAGER' ? '/admin/users' : '/x'),
          },
        },
      ],
    });

    navigateByUrl = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture = TestBed.createComponent(Login);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('does not call the API for an empty form or a malformed email', async () => {
    await setup(() => of(MANAGER_RESPONSE));

    await submit();
    expect(login).not.toHaveBeenCalled();

    type('#li-email', 'not-an-email');
    type('#li-pw', 'whatever');
    await submit();
    expect(login).not.toHaveBeenCalled();
  });

  it('signs in and sends the user to their role home', async () => {
    await setup(() => of(MANAGER_RESPONSE));

    type('#li-email', 'manager@tech-connect.app');
    type('#li-pw', 'a-password');
    await submit();

    expect(login).toHaveBeenCalledWith('manager@tech-connect.app', 'a-password', true);
    expect(navigateByUrl).toHaveBeenCalledWith('/admin/users');
  });

  it('shows the API explanation on a 401', async () => {
    await setup(() =>
      throwError(
        () => new HttpErrorResponse({ status: 401, error: { error: 'Invalid credentials' } }),
      ),
    );

    type('#li-email', 'manager@tech-connect.app');
    type('#li-pw', 'wrong');
    await submit();

    expect(el.querySelector('.login-error-text')?.textContent).toContain('Invalid credentials');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('falls back to a generic message when the failure is not a 401', async () => {
    await setup(() => throwError(() => new HttpErrorResponse({ status: 500 })));

    type('#li-email', 'manager@tech-connect.app');
    type('#li-pw', 'a-password');
    await submit();

    expect(el.querySelector('.login-error-text')?.textContent).toContain('Could not sign in');
  });

  it('disables the button while signing in and ignores a second submit', async () => {
    const pending = new Subject<LoginResponse>();
    await setup(() => pending);

    type('#li-email', 'manager@tech-connect.app');
    type('#li-pw', 'a-password');
    await submit();
    await submit();

    expect(login).toHaveBeenCalledTimes(1);
    expect(el.querySelector<HTMLButtonElement>('.login-submit')!.disabled).toBe(true);
  });
});
