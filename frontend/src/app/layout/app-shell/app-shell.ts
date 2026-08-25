import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AccountService } from '../../core/auth/account.service';
import { UserRole } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ChangePasswordDialog } from '../change-password-dialog/change-password-dialog';
import { ProfileDialog } from '../profile-dialog/profile-dialog';

interface NavItem {
  readonly label: string;
  readonly icon: string;
  readonly route: string;
  readonly roles: readonly UserRole[];
}

/**
 * Navigation for the whole product. Only the admin entry is live in this milestone; the
 * incident and job areas are declared here so the controller dashboard and technician PWA
 * drop into an existing frame rather than needing the shell rebuilt around them.
 */
const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Incidents', icon: 'inbox', route: '/incidents', roles: ['CONTROLLER'] },
  { label: 'My jobs', icon: 'construction', route: '/jobs', roles: ['TECHNICIAN'] },
  { label: 'Users', icon: 'group', route: '/admin/users', roles: ['MANAGER'] },
  { label: 'Audit trail', icon: 'history', route: '/admin/audit', roles: ['MANAGER'] },
];

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatButtonModule,
    MatDialogModule,
    MatIconModule,
    MatListModule,
    MatMenuModule,
    MatSidenavModule,
    MatToolbarModule,
  ],
  templateUrl: './app-shell.html',
  styleUrl: './app-shell.scss',
})
export class AppShell implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly account = inject(AccountService);
  private readonly dialog = inject(MatDialog);

  readonly displayName = this.auth.displayName;
  readonly role = this.auth.role;

  ngOnInit(): void {
    // A stored token can still be signed and unexpired yet already revoked — the backend
    // invalidates sessions on offboarding, a role change, or a password reset rather than
    // waiting for the 8-hour expiry. Confirming it once here means a dead session lands the
    // user on the login screen immediately, instead of rendering a shell that fails on the
    // first thing they click. The auth interceptor handles the 401 and signs them out.
    this.account.me().subscribe({ error: () => undefined });
  }

  /** Collapsed by default on narrow screens; the template drives the mode off a media query. */
  readonly sidenavOpen = signal(true);

  /**
   * Built from the signed-in role, so a Technician is never shown an admin link that would
   * only fail with a 403 once opened.
   */
  readonly navItems = computed(() => {
    const role = this.role();
    return role === null ? [] : NAV_ITEMS.filter((item) => item.roles.includes(role));
  });

  toggleSidenav(): void {
    this.sidenavOpen.update((open) => !open);
  }

  editProfile(): void {
    this.dialog.open(ProfileDialog, { width: '28rem' });
  }

  changePassword(): void {
    this.dialog.open(ChangePasswordDialog, { width: '26rem' });
  }

  logout(): void {
    this.auth.logout();
  }
}
