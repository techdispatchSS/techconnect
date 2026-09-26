import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { AccountService } from '../../../core/auth/account.service';
import { SetPassword, SetPasswordMode } from './set-password';

const GOOD_PASSWORD = 'Sup3r-secret-pass-123';

describe('SetPassword', () => {
  let fixture: ComponentFixture<SetPassword>;
  let el: HTMLElement;
  let activate: ReturnType<typeof vi.fn>;
  let resetPassword: ReturnType<typeof vi.fn>;

  async function setup(options: {
    mode: SetPasswordMode;
    token?: string;
    response?: () => Observable<void>;
  }): Promise<void> {
    const response = options.response ?? (() => of(undefined));
    activate = vi.fn(response);
    resetPassword = vi.fn(response);

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: AccountService, useValue: { activate, resetPassword } },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              data: { mode: options.mode },
              queryParamMap: convertToParamMap(options.token ? { token: options.token } : {}),
            },
          },
        },
      ],
    });

    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(SetPassword);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  async function submit(password = GOOD_PASSWORD, confirm = GOOD_PASSWORD): Promise<void> {
    type('#sp-password', password);
    type('#sp-confirm', confirm);
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  const linkTargets = () =>
    [...el.querySelectorAll('a')].map((a) => [a.textContent?.trim(), a.getAttribute('href')]);

  describe('token states', () => {
    it('explains a link with no token and offers a way to get a new one', async () => {
      await setup({ mode: 'ACTIVATE' });

      expect(el.querySelector('form')).toBeNull();
      expect(el.querySelector('[role="alert"]')?.textContent).toContain('missing its token');
      expect(linkTargets()).toContainEqual(['Request a new link', '/forgot-password']);
    });

    it('explains an invalid or expired token and keeps a request-a-new-link path', async () => {
      await setup({
        mode: 'RESET',
        token: 'stale',
        response: () => throwError(() => new HttpErrorResponse({ status: 400 })),
      });

      await submit();

      const alert = el.querySelector('[role="alert"]')?.textContent ?? '';
      expect(alert).toContain('invalid or has expired');
      expect(alert).toContain('single-use');
      expect(linkTargets()).toContainEqual(['Request a new link', '/forgot-password']);
      expect(el.querySelector<HTMLButtonElement>('.auth-submit')!.disabled).toBe(false);
    });

    it('uses a generic message for failures that are not a bad token', async () => {
      await setup({
        mode: 'RESET',
        token: 'abc',
        response: () => throwError(() => new HttpErrorResponse({ status: 500 })),
      });

      await submit();

      expect(el.querySelector('[role="alert"]')?.textContent).toContain(
        'Could not set your password',
      );
    });
  });

  describe('form', () => {
    it('does not submit mismatched or too-short passwords', async () => {
      await setup({ mode: 'RESET', token: 'abc' });

      await submit(GOOD_PASSWORD, 'something-else-entirely');
      expect(resetPassword).not.toHaveBeenCalled();
      expect(el.textContent).toContain('Passwords do not match');

      await submit('short', 'short');
      expect(resetPassword).not.toHaveBeenCalled();
    });

    it('resets with the token from the link', async () => {
      await setup({ mode: 'RESET', token: 'abc' });

      await submit();

      expect(resetPassword).toHaveBeenCalledWith('abc', GOOD_PASSWORD);
      expect(activate).not.toHaveBeenCalled();
      expect(el.querySelector('[role="status"]')?.textContent).toContain('Password set');
    });

    it('activates instead when opened from an invite link', async () => {
      await setup({ mode: 'ACTIVATE', token: 'invite-token' });

      await submit();

      expect(activate).toHaveBeenCalledWith('invite-token', GOOD_PASSWORD);
      expect(resetPassword).not.toHaveBeenCalled();
    });

    it('disables the button while saving and ignores a second submit', async () => {
      await setup({ mode: 'RESET', token: 'abc', response: () => new Subject<void>() });

      await submit();
      await submit();

      expect(resetPassword).toHaveBeenCalledTimes(1);
      expect(el.querySelector<HTMLButtonElement>('.auth-submit')!.disabled).toBe(true);
    });
  });
});
