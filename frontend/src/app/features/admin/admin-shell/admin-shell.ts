import { BreakpointObserver } from '@angular/cdk/layout';
import {
  Component,
  OnInit,
  ViewEncapsulation,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatMenuModule } from '@angular/material/menu';
import {
  ActivatedRoute,
  NavigationEnd,
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet,
} from '@angular/router';
import { filter, map } from 'rxjs';

import { AccountService } from '../../../core/auth/account.service';
import { AuthService } from '../../../core/auth/auth.service';
import { ChangePasswordDialog } from '../../../layout/change-password-dialog/change-password-dialog';
import { ProfileDialog } from '../../../layout/profile-dialog/profile-dialog';
import { AdminNavCountsService } from '../admin-nav-counts.service';
import { AdminIcon } from '../ui/admin-icon/admin-icon';

interface NavItem {
  readonly label: string;
  readonly route: string;
  readonly icon: 'people' | 'add' | 'invite' | 'audit';
  readonly count: () => number | null;
}

/**
 * Owns the admin portal's own frame — sidebar, header and the routed screen underneath —
 * rather than nesting inside the generic `AppShell`. Manager is the only role that reaches
 * this section (M-06 §"Admin portal design mockup"), so it gets a dedicated, purpose-built
 * shell instead of sharing chrome built for a multi-role product surface.
 */
@Component({
  selector: 'app-admin-shell',
  encapsulation: ViewEncapsulation.None,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatDialogModule, MatMenuModule, AdminIcon],
  templateUrl: './admin-shell.html',
  styleUrl: './admin-shell.scss',
})
export class AdminShell implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly account = inject(AccountService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly breakpointObserver = inject(BreakpointObserver);
  readonly counts = inject(AdminNavCountsService);

  private static readonly HANDSET_QUERY = '(max-width: 899px)';

  readonly isHandset = toSignal(
    this.breakpointObserver.observe(AdminShell.HANDSET_QUERY).pipe(map((result) => result.matches)),
    { initialValue: this.breakpointObserver.isMatched(AdminShell.HANDSET_QUERY) },
  );

  /** Open by default on desktop, collapsed by default on a handset — resynced whenever the
   * viewport crosses the breakpoint, without fighting a toggle made earlier in the same one. */
  readonly sidebarOpen = signal(!this.isHandset());

  constructor() {
    effect(() => {
      this.sidebarOpen.set(!this.isHandset());
    });
  }

  readonly displayName = this.auth.displayName;
  readonly role = this.auth.role;

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

  readonly navItems: readonly NavItem[] = [
    {
      label: 'People',
      route: '/admin/users',
      icon: 'people',
      count: () => this.counts.peopleCount(),
    },
    { label: 'Add user', route: '/admin/users/new', icon: 'add', count: () => null },
    {
      label: 'Invites',
      route: '/admin/invites',
      icon: 'invite',
      count: () => this.counts.pendingInviteCount(),
    },
    { label: 'Audit trail', route: '/admin/audit', icon: 'audit', count: () => null },
  ];

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** The active route's own heading (set via `data.heading`), read fresh on every navigation
   * since child `ActivatedRoute` instances are replaced rather than mutated. */
  readonly heading = computed(() => {
    this.currentUrl();
    let current = this.route.snapshot;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return (current.data['heading'] as string | undefined) ?? 'Admin';
  });

  /** The add-user page is itself the primary action the button offers, so hide it there. */
  readonly showAddUser = computed(() => !this.currentUrl().startsWith('/admin/users/new'));

  ngOnInit(): void {
    // A stored token can still be signed and unexpired yet already revoked by the backend —
    // confirm the session once here so a dead one lands on the login screen immediately.
    this.account.me().subscribe({ error: () => undefined });
    this.counts.refresh();
  }

  toggleSidebar(): void {
    this.sidebarOpen.update((open) => !open);
  }

  closeSidebarOnHandset(): void {
    if (this.isHandset()) {
      this.sidebarOpen.set(false);
    }
  }

  editProfile(): void {
    this.dialog.open(ProfileDialog, { width: '28rem', panelClass: 'pd-dialog-panel' });
  }

  changePassword(): void {
    this.dialog.open(ChangePasswordDialog, { width: '26rem', panelClass: 'cpd-dialog-panel' });
  }

  logout(): void {
    this.auth.logout();
  }
}
