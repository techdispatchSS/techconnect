import { Component, inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';

export interface ConfirmDialogData {
  title: string;
  message: string;
  confirmLabel: string;
  /** Renders the confirm action as the destructive (red, outlined) button variant. */
  destructive?: boolean;
}

/**
 * Generic confirmation. Closes with `true` only when the user explicitly confirms.
 *
 * <p>Buttons render with the `.admin-btn` classes used everywhere else in the admin feature
 * rather than Material's own button theming — this dialog is portaled to a `cdk-overlay-container`
 * outside `.admin-shell`'s DOM subtree, so it can't inherit that theme's CSS custom properties,
 * and previously fell back to the app's global (light) Material theme regardless of which admin
 * screen opened it. See the `.admin-confirm-dialog` panel-class styles in admin-shell.scss.
 */
@Component({
  selector: 'app-confirm-dialog',
  imports: [MatDialogModule],
  templateUrl: './confirm-dialog.html',
})
export class ConfirmDialog {
  readonly data = inject<ConfirmDialogData>(MAT_DIALOG_DATA);
}
