import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AccountService } from '../../../core/auth/account.service';
import { AuthService } from '../../../core/auth/auth.service';
import { TechnicianShell } from './technician-shell';

describe('TechnicianShell', () => {
  let logout: ReturnType<typeof vi.fn>;
  let open: ReturnType<typeof vi.fn>;

  function setup(): ComponentFixture<TechnicianShell> {
    logout = vi.fn();
    open = vi.fn();
    TestBed.configureTestingModule({
      imports: [TechnicianShell],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: AuthService, useValue: { displayName: signal('Thandi Mokoena'), logout } },
        { provide: AccountService, useValue: { me: () => of({}) } },
        { provide: MatDialog, useValue: { open } },
      ],
    });
    const fixture = TestBed.createComponent(TechnicianShell);
    fixture.detectChanges();
    return fixture;
  }

  it('has the account in the rail, not the header, like the other portals', () => {
    const el = setup().nativeElement as HTMLElement;

    const account = el.querySelector('.controller-shell-sidebar .controller-shell-user');
    expect(account?.getAttribute('aria-label')).toBe('Account menu for Thandi Mokoena');
    expect(account?.textContent).toContain('TM');
    expect(account?.textContent).toContain('Technician');
    expect(el.querySelector('.controller-shell-header .controller-shell-user')).toBeNull();
  });

  it('links to My jobs and titles the page', () => {
    const el = setup().nativeElement as HTMLElement;

    const link = el.querySelector('.controller-shell-nav-item');
    expect(link?.getAttribute('href')).toBe('/jobs');
    expect(el.querySelector('.controller-shell-title')?.textContent).toContain('My jobs');
  });

  it('signs out through the auth service', () => {
    const fixture = setup();

    fixture.componentInstance.logout();

    expect(logout).toHaveBeenCalled();
  });
});
