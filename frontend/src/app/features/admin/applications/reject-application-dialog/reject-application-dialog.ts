import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';

import { requiredTrimmed } from '../../../../shared/form-text';

export interface RejectApplicationDialogData {
  applicantName: string;
}

/**
 * Collects the mandatory reason for rejecting an application. Closes with the trimmed reason, or
 * undefined if cancelled.
 *
 * <p>Styled with literal colours rather than the admin CSS custom properties: like the confirm
 * dialog, it is portaled to the overlay container, outside `.admin-shell`, so those variables
 * don't resolve here.
 */
@Component({
  selector: 'app-reject-application-dialog',
  imports: [ReactiveFormsModule, MatDialogModule],
  templateUrl: './reject-application-dialog.html',
  styleUrl: './reject-application-dialog.scss',
})
export class RejectApplicationDialog {
  readonly data = inject<RejectApplicationDialogData>(MAT_DIALOG_DATA);
  private readonly ref = inject<MatDialogRef<RejectApplicationDialog, string>>(MatDialogRef);

  readonly reason = new FormControl('', {
    nonNullable: true,
    validators: [requiredTrimmed, Validators.maxLength(1000)],
  });

  confirm(): void {
    if (this.reason.invalid) {
      this.reason.markAsTouched();
      return;
    }
    this.ref.close(this.reason.value.trim());
  }
}
