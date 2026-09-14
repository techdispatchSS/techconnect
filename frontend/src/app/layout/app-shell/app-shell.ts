import { BreakpointObserver } from '@angular/cdk/layout';
import { Component, OnInit, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import {
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';

import { AccountService } from '../../core/auth/account.service';
import { UserRole } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';
import { ThemeService } from '../../core/theme/theme.service';
import { ChangePasswordDialog } from '../change-password-dialog/change-password-dialog';
import { ProfileDialog } from '../profile-dialog/profile-dialog';

interface NavItem {
  readonly label: string;
  readonly icon: string;
  readonly route: string;
  readonly roles: readonly UserRole[];
}

/**
 * Navigation for the non-admin product surface. The Manager admin portal (`AdminShell`) and
 * the Controller dashboard (`ControllerShell`) both have their own dedicated shells and never
 * reach this one — this array is left for roles whose product surface hasn't earned a
 * bespoke shell of its own, such as the technician PWA below.
 */
const NAV_ITEMS: readonly NavItem[] = [
  { label: 'My jobs', icon: 'construction', route: '/jobs', roles: ['TECHNICIAN'] },
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
  private readonly router = inject(Router);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly themeService = inject(ThemeService);

  readonly theme = this.themeService.theme;

  toggleTheme(): void {
    this.themeService.toggle();
  }

  /** Below this the sidenav can no longer share the viewport with content — same threshold
   * as the tablet breakpoint used throughout app-shell.scss. */
  private static readonly HANDSET_QUERY = '(max-width: 767px)';

  readonly isHandset = toSignal(
    this.breakpointObserver
      .observe(AppShell.HANDSET_QUERY)
      .pipe(map((result) => result.matches)),
    { initialValue: this.breakpointObserver.isMatched(AppShell.HANDSET_QUERY) },
  );

  /** `side` pins the sidenav open alongside content on desktop; `over` makes it a
   * dismissible overlay on a handset, where pinning it open would just crush the content
   * into a sliver instead. */
  readonly sidenavMode = computed<'over' | 'side'>(() => (this.isHandset() ? 'over' : 'side'));

  readonly displayName = this.auth.displayName;
  readonly role = this.auth.role;

  /** Two-letter monogram for the toolbar avatar chip, e.g. "Jane Ortiz" → "JO". */
  readonly initials = computed(() => {
    const name = this.displayName();
    if (!name) {
      return '';
    }
    const parts = name.trim().split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
    return (first + last).toUpperCase();
  });

  ngOnInit(): void {
    // A stored token can still be signed and unexpired yet already revoked — the backend
    // invalidates sessions on offboarding, a role change, or a password reset rather than
    // waiting for the 8-hour expiry. Confirming it once here means a dead session lands the
    // user on the login screen immediately, instead of rendering a shell that fails on the
    // first thing they click. The auth interceptor handles the 401 and signs them out.
    this.account.me().subscribe({ error: () => undefined });
  }

  /** Open by default on desktop, collapsed by default on a handset. */
  readonly sidenavOpen = signal(!this.isHandset());

  constructor() {
    // Re-syncs open/closed whenever the viewport crosses the handset breakpoint (e.g. a
    // tablet rotated, or a window resized), without fighting a toggle the user made earlier
    // in the same breakpoint — this only runs again once `isHandset()` itself flips.
    effect(() => {
      this.sidenavOpen.set(!this.isHandset());
    });
  }

  /**
   * Built from the signed-in role, so a Technician is never shown an admin link that would
   * only fail with a 403 once opened.
   */
  readonly navItems = computed(() => {
    const role = this.role();
    return role === null ? [] : NAV_ITEMS.filter((item) => item.roles.includes(role));
  });

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** Breadcrumb label for the toolbar — the nav entry whose route most specifically
   * matches the current URL, so a nested page like `/admin/users/:id` still reads "Users". */
  readonly pageTitle = computed(() => {
    const url = this.currentUrl();
    const match = NAV_ITEMS.filter((item) => url.startsWith(item.route)).sort(
      (a, b) => b.route.length - a.route.length,
    )[0];
    return match?.label ?? '';
  });

  toggleSidenav(): void {
    this.sidenavOpen.update((open) => !open);
  }

  /** On a handset the sidenav is a dismissible overlay — leave it open after the user has
   * already told it where they want to go and it would otherwise just sit on top of the page. */
  closeSidenavOnHandset(): void {
    if (this.isHandset()) {
      this.sidenavOpen.set(false);
    }
  }

  editProfile(): void {
    // Same dialog the admin portal opens — its content now follows that design system
    // regardless of which shell opens it (see profile-dialog.ts), so the panel class keeps
    // the outer frame square to match rather than clashing with the squared-off content.
    this.dialog.open(ProfileDialog, { width: '28rem', panelClass: 'pd-dialog-panel' });
  }

  changePassword(): void {
    this.dialog.open(ChangePasswordDialog, { width: '26rem', panelClass: 'cpd-dialog-panel' });
  }

  logout(): void {
    this.auth.logout();
  }
}
