import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { debounceTime, distinctUntilChanged } from 'rxjs';

import { Skeleton } from '../../../../shared/skeleton/skeleton';
import { AdminApplicationService } from '../../admin-application.service';
import {
  APPLICATION_STATUS_LABELS,
  ApplicationStatus,
  ApplicationSummary,
} from '../../admin.models';
import { AdminIcon } from '../../ui/admin-icon/admin-icon';

interface StatusTab {
  readonly label: string;
  readonly status: ApplicationStatus | null;
}

/**
 * Every technician application, newest first, with "awaiting review" as the default view since
 * that is the Manager's work queue. Filtering, search and paging happen in the database.
 */
@Component({
  selector: 'app-application-list',
  imports: [DatePipe, ReactiveFormsModule, AdminIcon, Skeleton],
  templateUrl: './application-list.html',
  styleUrl: './application-list.scss',
})
export class ApplicationList implements OnInit {
  private readonly api = inject(AdminApplicationService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly statusLabels = APPLICATION_STATUS_LABELS;
  readonly tabs: readonly StatusTab[] = [
    { label: 'Awaiting review', status: 'PENDING' },
    { label: 'Approved', status: 'APPROVED' },
    { label: 'Rejected', status: 'REJECTED' },
    { label: 'All', status: null },
  ];

  readonly statusFilter = signal<ApplicationStatus | null>('PENDING');
  readonly searchControl = new FormControl('', { nonNullable: true });

  readonly applications = signal<ApplicationSummary[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly loadFailed = signal(false);

  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);
  readonly skeletonRows = [1, 2, 3, 4, 5];

  readonly pageFrom = computed(() =>
    this.totalElements() === 0 ? 0 : this.pageIndex() * this.pageSize() + 1,
  );
  readonly pageTo = computed(() =>
    Math.min(this.totalElements(), (this.pageIndex() + 1) * this.pageSize()),
  );

  constructor() {
    this.searchControl.valueChanges
      .pipe(debounceTime(300), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.pageIndex.set(0);
        this.load();
      });
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.loadFailed.set(false);

    this.api
      .list({
        status: this.statusFilter(),
        q: this.searchControl.value,
        page: this.pageIndex(),
        size: this.pageSize(),
      })
      .subscribe({
        next: (page) => {
          this.applications.set(page.content);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
          this.loaded.set(true);
        },
        error: () => {
          this.applications.set([]);
          this.totalElements.set(0);
          this.loading.set(false);
          this.loaded.set(true);
          this.loadFailed.set(true);
        },
      });
  }

  onTab(status: ApplicationStatus | null): void {
    this.statusFilter.set(status);
    this.pageIndex.set(0);
    this.load();
  }

  open(application: ApplicationSummary): void {
    void this.router.navigate(['/admin/applications', application.id]);
  }

  onPrevPage(): void {
    if (this.pageIndex() > 0) {
      this.pageIndex.update((i) => i - 1);
      this.load();
    }
  }

  onNextPage(): void {
    if (this.pageTo() < this.totalElements()) {
      this.pageIndex.update((i) => i + 1);
      this.load();
    }
  }

  tagClass(status: ApplicationStatus): string {
    return status === 'APPROVED'
      ? 'admin-tag-active'
      : status === 'REJECTED'
        ? 'admin-tag-disabled'
        : 'admin-tag-pending';
  }
}
