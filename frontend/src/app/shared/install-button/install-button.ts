import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  inject,
  input,
} from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { InstallPlatform, InstallService } from '../../core/pwa/install.service';

/** The steps for browsers that cannot show an install dialog from a button. */
@Component({
  selector: 'app-install-help',
  imports: [MatDialogModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  template: `
    <div class="ih">
      <h2 class="ih-title">Install TechConnect</h2>
      <p class="ih-lede">Add it to your {{ where }} so it opens like any other app.</p>
      <ol class="ih-steps">
        @if (platform === 'ios') {
          <li>
            Open this page in <strong>Safari</strong> (other iPhone browsers can't install apps).
          </li>
          <li>Tap the <strong>Share</strong> button, the square with an arrow, in the toolbar.</li>
          <li>
            Scroll down and choose <strong>Add to Home Screen</strong>, then tap
            <strong>Add</strong>.
          </li>
        } @else if (platform === 'android') {
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
      <div class="ih-actions">
        <button type="button" class="ih-btn" mat-dialog-close>Got it</button>
      </div>
    </div>
  `,
  styles: `
    .ih {
      padding: 22px 24px 20px;
      font-family: 'Barlow', system-ui, sans-serif;
      color: var(--tc-pop-text);
    }
    .ih-title {
      margin: 0 0 4px;
      font-size: 22px;
      font-weight: 600;
      letter-spacing: -0.01em;
    }
    .ih-lede {
      margin: 0 0 14px;
      font-size: 14px;
      color: var(--tc-pop-muted);
    }
    .ih-steps {
      margin: 0;
      padding-left: 1.2rem;
      display: flex;
      flex-direction: column;
      gap: 10px;
      font-size: 14.5px;
      line-height: 1.5;
    }
    .ih-actions {
      display: flex;
      justify-content: flex-end;
      margin-top: 20px;
    }
    .ih-btn {
      cursor: pointer;
      min-height: 40px;
      padding: 0 20px;
      font:
        600 14px 'Barlow',
        system-ui,
        sans-serif;
      color: #12140f;
      background: var(--tc-accent);
      border: 1px solid var(--tc-accent);
      border-radius: 0;
    }
    .ih-btn:hover {
      background: #ff9540;
      border-color: #ff9540;
    }
  `,
})
export class InstallHelp {
  readonly platform: InstallPlatform = inject(InstallService).platform;

  get where(): string {
    return this.platform === 'desktop' ? 'computer' : 'home screen';
  }
}

/**
 * "Install app". Shown wherever it is useful to get to quickly (sign-in, the application form and
 * every signed-in header) and hidden once the app is installed. Where the browser can show its
 * own install dialog this opens it; elsewhere it explains the steps.
 */
@Component({
  selector: 'app-install-button',
  imports: [MatDialogModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styleUrl: './install-button.scss',
  template: `
    @if (installer.available()) {
      <button
        type="button"
        class="ib"
        [class.ib--block]="block()"
        [class.ib--compact]="compact()"
        (click)="onClick()"
      >
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          stroke-width="1.6"
          aria-hidden="true"
        >
          <rect x="6.5" y="2.5" width="11" height="19" rx="1.5" />
          <path d="M12 7.5v7M9 12l3 3 3-3M10.5 18.5h3" />
        </svg>
        <span class="ib-label">{{ label() }}</span>
      </button>
    }
  `,
})
export class InstallButton {
  protected readonly installer = inject(InstallService);
  private readonly dialog = inject(MatDialog);

  readonly label = input('Install app');
  /** Fill the width of the container (the sign-in page). */
  readonly block = input(false);
  /** Icon only on a narrow screen, so a header with many controls still fits. */
  readonly compact = input(false);

  async onClick(): Promise<void> {
    const outcome = await this.installer.install();
    if (outcome === 'manual') {
      this.dialog.open(InstallHelp, { width: '26rem', panelClass: 'tc-dialog-panel' });
    }
  }
}
