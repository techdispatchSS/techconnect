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
import { ThemeService } from '../../../core/theme/theme.service';
import { ChangePasswordDialog } from '../../../layout/change-password-dialog/change-password-dialog';
import { ProfileDialog } from '../../../layout/profile-dialog/profile-dialog';
import { InstallButton } from '../../../shared/install-button/install-button';
import { Logo } from '../../../shared/logo/logo';

/**
 * The technician app's frame: the same rail, header and account button as the controller
 * dashboard, so a person moving between the two is in one product. It deliberately shares
 * `controller-shell.scss` (the `controller-` classes and tokens, the phone drawer, the light
 * theme) rather than a copy that would drift. The screens inside use those classes too.
 */
@Component({
  selector: 'app-technician-shell',
  encapsulation: ViewEncapsulation.None,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatDialogModule,
    MatMenuModule,
    Logo,
    InstallButton,
  ],
  templateUrl: './technician-shell.html',
  styleUrl: '../../controller/controller-shell/controller-shell.scss',
})
export class TechnicianShell implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly account = inject(AccountService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly themeService = inject(ThemeService);

  readonly theme = this.themeService.theme;

  private static readonly HANDSET_QUERY = '(max-width: 899px)';

  readonly isHandset = toSignal(
    this.breakpointObserver
      .observe(TechnicianShell.HANDSET_QUERY)
      .pipe(map((result) => result.matches)),
    { initialValue: this.breakpointObserver.isMatched(TechnicianShell.HANDSET_QUERY) },
  );

  readonly sidebarOpen = signal(!this.isHandset());

  constructor() {
    effect(() => {
      this.sidebarOpen.set(!this.isHandset());
    });
  }

  readonly displayName = this.auth.displayName;

  readonly initials = computed(() => {
    const parts = this.displayName().trim().split(/\s+/);
    const first = parts[0]?.[0] ?? '';
    const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
    return (first + last).toUpperCase();
  });

  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map((event) => event.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  readonly heading = computed(() => {
    this.currentUrl();
    let current = this.route.snapshot;
    while (current.firstChild) {
      current = current.firstChild;
    }
    return (current.data['heading'] as string | undefined) ?? 'My jobs';
  });

  ngOnInit(): void {
    // A stored token can still be signed and unexpired yet already revoked by the backend —
    // confirm the session once here so a dead one lands on the login screen immediately.
    this.account.me().subscribe({ error: () => undefined });
  }

  toggleTheme(): void {
    this.themeService.toggle();
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
    this.dialog.open(ProfileDialog, {
      width: '30rem',
      maxWidth: '94vw',
      panelClass: ['tc-dialog-panel', 'pd-dialog-panel'],
    });
  }

  changePassword(): void {
    this.dialog.open(ChangePasswordDialog, {
      width: '26rem',
      maxWidth: '94vw',
      panelClass: ['tc-dialog-panel', 'cpd-dialog-panel'],
    });
  }

  logout(): void {
    this.auth.logout();
  }
}
