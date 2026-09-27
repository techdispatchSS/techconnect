import { BreakpointObserver } from '@angular/cdk/layout';
import {
  Component,
  DestroyRef,
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
import { PRIORITY_LABELS } from '../controller.models';
import { IncidentAlert, NewIncidentNotifierService } from '../new-incident-notifier.service';

/**
 * Owns the controller dashboard's own frame — sidebar, header and the routed screen
 * underneath — the same reasoning as `AdminShell`: CONTROLLER is the only role that reaches
 * this section, so it gets a purpose-built shell rather than sharing chrome built for a
 * multi-role product surface (see the design mockup, "TechConnect Controller Dashboard").
 */
@Component({
  selector: 'app-controller-shell',
  encapsulation: ViewEncapsulation.None,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatDialogModule, MatMenuModule],
  templateUrl: './controller-shell.html',
  styleUrl: './controller-shell.scss',
})
export class ControllerShell implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly account = inject(AccountService);
  private readonly dialog = inject(MatDialog);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly themeService = inject(ThemeService);
  private readonly destroyRef = inject(DestroyRef);
  readonly notifier = inject(NewIncidentNotifierService);

  readonly priorityLabels = PRIORITY_LABELS;

  readonly theme = this.themeService.theme;

  toggleTheme(): void {
    this.themeService.toggle();
  }

  private static readonly HANDSET_QUERY = '(max-width: 899px)';

  readonly isHandset = toSignal(
    this.breakpointObserver
      .observe(ControllerShell.HANDSET_QUERY)
      .pipe(map((result) => result.matches)),
    { initialValue: this.breakpointObserver.isMatched(ControllerShell.HANDSET_QUERY) },
  );

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

  /** New-incident count badges the "Incident queue" nav entry, matching the design mockup —
   * sourced live from `notifier`, the same poll that drives the toast alerts below. */
  readonly newCount = this.notifier.newCount;

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
    return (current.data['heading'] as string | undefined) ?? 'Dispatch';
  });

  ngOnInit(): void {
    // A stored token can still be signed and unexpired yet already revoked by the backend —
    // confirm the session once here so a dead one lands on the login screen immediately.
    this.account.me().subscribe({ error: () => undefined });
    this.notifier.start(this.destroyRef);
  }

  /** Sends the Controller to the queue with this incident selected — new incidents always
   * still need a look before dispatching, so this stops short of the dispatch form itself. */
  openAlert(alert: IncidentAlert): void {
    this.notifier.dismiss(alert.id);
    void this.router.navigate(['/incidents/queue'], {
      queryParams: { selected: alert.incident.id },
    });
  }

  dismissAlert(event: Event, id: string): void {
    event.stopPropagation();
    this.notifier.dismiss(id);
  }

  alertPriorityClass(alert: IncidentAlert): string {
    switch (alert.incident.priority) {
      case 'HIGH':
        return 'controller-pill controller-pill--high';
      case 'MEDIUM':
        return 'controller-pill controller-pill--medium';
      default:
        return 'controller-pill controller-pill--low';
    }
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
