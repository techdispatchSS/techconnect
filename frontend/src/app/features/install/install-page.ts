import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { InstallService } from '../../core/pwa/install.service';
import { InstallButton } from '../../shared/install-button/install-button';
import { Logo } from '../../shared/logo/logo';

/**
 * Where the "install the app" link in a technician's welcome email lands. It works on the phone
 * it is opened on: a button where the browser can install straight away, and the steps for the
 * browsers that cannot (iPhone Safari above all).
 *
 * <p>Public and reachable by anyone with the link; it reveals nothing about any account.
 */
@Component({
  selector: 'app-install-page',
  imports: [RouterLink, Logo, InstallButton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrls: ['../auth/auth-page.scss', './install-page.scss'],
  template: `
    <div class="auth-card ip-card">
      <h1 class="auth-brand"><app-logo variant="lockup" height="38px" /></h1>
      <h2 class="auth-heading">Install the TechConnect app</h2>

      @if (installer.installed()) {
        <p class="auth-notice" role="status">
          TechConnect is already installed on this device. Open it from your home screen or app
          list.
        </p>
        <div class="auth-links"><a routerLink="/login">Go to sign in</a></div>
      } @else {
        <p class="ip-lede">
          Put TechConnect on your phone so new jobs are one tap away, and it opens full screen like
          any other app.
        </p>

        <div class="ip-install">
          <app-install-button [block]="true" label="Install the app" />
        </div>

        <h3 class="ip-steps-title">Or add it yourself</h3>
        <ol class="ip-steps">
          @if (installer.platform === 'ios') {
            <li>
              Open this page in <strong>Safari</strong>. Other iPhone browsers can't install apps.
            </li>
            <li>
              Tap the <strong>Share</strong> button, the square with an arrow, in the toolbar.
            </li>
            <li>
              Scroll down, choose <strong>Add to Home Screen</strong>, then tap
              <strong>Add</strong>.
            </li>
          } @else if (installer.platform === 'android') {
            <li>Open the browser menu (<strong>⋮</strong>, top right).</li>
            <li>Choose <strong>Install app</strong> or <strong>Add to Home screen</strong>.</li>
            <li>Confirm, and TechConnect appears with your other apps.</li>
          } @else {
            <li>
              In Chrome or Edge, click the <strong>install</strong> icon at the right of the address
              bar, or open the browser menu and choose <strong>Install TechConnect</strong>.
            </li>
            <li>Confirm, and it opens in its own window.</li>
          }
        </ol>

        <div class="auth-links"><a routerLink="/login">Back to sign in</a></div>
      }
    </div>
  `,
})
export class InstallPage {
  protected readonly installer = inject(InstallService);
}
