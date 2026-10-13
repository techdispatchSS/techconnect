import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, signal } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { PROVINCE_LABELS } from '../../../../core/address.models';
import { ConfirmDialog, ConfirmDialogData } from '../../../../shared/confirm-dialog/confirm-dialog';
import { parseSkills } from '../../../../shared/skill-list';
import { Skeleton } from '../../../../shared/skeleton/skeleton';
import { AdminApplicationService } from '../../admin-application.service';
import { AdminNavCountsService } from '../../admin-nav-counts.service';
import {
  APPLICATION_STATUS_LABELS,
  ApplicationDetail,
  ApplicationDocument,
  DOCUMENT_KIND_LABELS,
} from '../../admin.models';
import { AdminIcon } from '../../ui/admin-icon/admin-icon';
import { RejectApplicationDialog } from '../reject-application-dialog/reject-application-dialog';

/**
 * One application, for the Manager to read, check the uploaded proof of certificates and
 * qualifications, and then accept or reject. Documents are only ever downloaded, never rendered
 * in the page: they come from an anonymous applicant and are treated as untrusted.
 */
@Component({
  selector: 'app-application-detail',
  imports: [DatePipe, RouterLink, MatDialogModule, MatSnackBarModule, AdminIcon, Skeleton],
  templateUrl: './application-detail.html',
  styleUrl: './application-detail.scss',
})
export class ApplicationDetailPage implements OnInit {
  private readonly api = inject(AdminApplicationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly counts = inject(AdminNavCountsService);

  readonly statusLabels = APPLICATION_STATUS_LABELS;
  readonly kindLabels = DOCUMENT_KIND_LABELS;
  readonly skillsOf = parseSkills;
  readonly kindLabels = DOCUMENT_KIND_LABELS;
  readonly provinceLabels = PROVINCE_LABELS;

  readonly application = signal<ApplicationDetail | null>(null);
  readonly loading = signal(true);
  readonly loadFailed = signal(false);
  readonly deciding = signal(false);
  readonly downloadingId = signal<string | null>(null);
  /** Shown once after approving: the single-use link the new technician activates with. */
  readonly activationUrl = signal<string | null>(null);

  private id = '';

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);
    this.api.get(this.id).subscribe({
      next: (application) => {
        this.application.set(application);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.loadFailed.set(true);
      },
    });
  }

  download(document: ApplicationDocument): void {
    this.downloadingId.set(document.id);
    this.api.download(this.id, document).subscribe({
      next: () => this.downloadingId.set(null),
      error: (error: unknown) => {
        this.downloadingId.set(null);
        this.snackBar.open(
          this.messageFrom(error, 'Could not download that document.'),
          'Dismiss',
          {
            duration: 6000,
          },
        );
      },
    });
  }

  approve(): void {
    const application = this.application();
    if (!application || this.deciding()) {
      return;
    }

    const data: ConfirmDialogData = {
      title: `Approve ${application.firstName} ${application.lastName}?`,
      message:
        'This creates their technician account and emails them a link to set a password. ' +
        'Only approve once you have checked their certificates and qualifications.',
      confirmLabel: 'Approve and create account',
    };

    this.dialog
      .open(ConfirmDialog, { data, width: '28rem', panelClass: 'admin-confirm-dialog' })
      .afterClosed()
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.deciding.set(true);
        this.api.approve(application.id).subscribe({
          next: (response) => {
            this.deciding.set(false);
            this.application.set(response.application);
            this.activationUrl.set(response.activationUrl);
            this.counts.refresh();
            this.snackBar.open('Approved. Their account has been created.', 'Dismiss', {
              duration: 5000,
            });
          },
          error: (error: unknown) => this.decisionFailed(error),
        });
      });
  }

  reject(): void {
    const application = this.application();
    if (!application || this.deciding()) {
      return;
    }

    this.dialog
      .open(RejectApplicationDialog, {
        data: { applicantName: `${application.firstName} ${application.lastName}` },
        width: '30rem',
        panelClass: 'admin-confirm-dialog',
      })
      .afterClosed()
      .subscribe((reason: string | undefined) => {
        if (!reason) {
          return;
        }
        this.deciding.set(true);
        this.api.reject(application.id, reason).subscribe({
          next: (updated) => {
            this.deciding.set(false);
            this.application.set(updated);
            this.counts.refresh();
            this.snackBar.open('Application rejected.', 'Dismiss', { duration: 5000 });
          },
          error: (error: unknown) => this.decisionFailed(error),
        });
      });
  }

  async copyActivationUrl(): Promise<void> {
    const url = this.activationUrl();
    if (!url) {
      return;
    }
    try {
      await navigator.clipboard.writeText(url);
      this.snackBar.open('Activation link copied.', 'Dismiss', { duration: 4000 });
    } catch {
      this.snackBar.open('Copy blocked by the browser. Select the link manually.', 'Dismiss', {
        duration: 6000,
      });
    }
  }

  back(): void {
    void this.router.navigate(['/admin/applications']);
  }

  formatBytes(bytes: number): string {
    return bytes < 1024 * 1024
      ? `${Math.max(1, Math.round(bytes / 1024))} KB`
      : `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  private decisionFailed(error: unknown): void {
    this.deciding.set(false);
    // Someone else may have decided it first; show the current state rather than a stale one.
    if (error instanceof HttpErrorResponse && error.status === 409) {
      this.load();
    }
    this.snackBar.open(this.messageFrom(error, 'Could not save that decision.'), 'Dismiss', {
      duration: 7000,
    });
  }

  private messageFrom(error: unknown, fallback: string): string {
    return error instanceof HttpErrorResponse
      ? ((error.error as { error?: string } | null)?.error ?? fallback)
      : fallback;
  }
}
