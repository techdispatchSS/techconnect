import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, computed, inject, signal } from '@angular/core';

import { AuthService } from '../../../core/auth/auth.service';
import { DeviceLocationService } from '../device-location.service';
import { TechnicianApiService } from '../technician-api.service';
import { MyAvailability } from '../technician.models';

type LocationState = 'unknown' | 'shared' | 'blocked';

/**
 * The technician's home: going on and off duty (T-14), and sharing where they are so the
 * controller can see how far away they are when dispatching (FR-05). The location goes up
 * when the page opens and again whenever the app comes back to the foreground.
 */
@Component({
  selector: 'app-technician-home',
  imports: [DatePipe],
  templateUrl: './technician-home.html',
  styleUrl: './technician-home.scss',
})
export class TechnicianHome {
  private readonly api = inject(TechnicianApiService);
  private readonly location = inject(DeviceLocationService);

  readonly name = inject(AuthService).displayName;
  readonly availability = signal<MyAvailability | null>(null);
  readonly loadFailed = signal(false);
  readonly saving = signal(false);
  readonly error = signal<string | null>(null);
  readonly locationState = signal<LocationState>('unknown');

  readonly onDuty = computed(() => this.availability()?.status !== 'OFFLINE');
  readonly headline = computed(() => {
    const availability = this.availability();
    if (!availability) {
      return '';
    }
    if (availability.onJob || availability.status === 'ON_JOB') {
      return "You're on a job";
    }
    return availability.status === 'AVAILABLE' ? "You're on duty" : "You're off duty";
  });

  constructor() {
    this.load();

    const onVisible = () => {
      if (document.visibilityState === 'visible' && this.availability()) {
        this.shareLocation();
      }
    };
    document.addEventListener('visibilitychange', onVisible);
    inject(DestroyRef).onDestroy(() =>
      document.removeEventListener('visibilitychange', onVisible),
    );
  }

  load(): void {
    this.loadFailed.set(false);
    this.api.availability().subscribe({
      next: (availability) => {
        this.availability.set(availability);
        this.shareLocation();
      },
      error: () => this.loadFailed.set(true),
    });
  }

  toggle(): void {
    const current = this.availability();
    if (!current || current.onJob || this.saving()) {
      return;
    }
    const goingOnDuty = current.status === 'OFFLINE';
    this.saving.set(true);
    this.error.set(null);
    this.api.setAvailable(goingOnDuty).subscribe({
      next: (availability) => {
        this.saving.set(false);
        this.availability.set(availability);
        if (goingOnDuty) {
          this.shareLocation();
        }
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.error.set(
          (error instanceof HttpErrorResponse &&
            (error.error as { error?: string } | null)?.error) ||
            "Couldn't update your status. Check your connection and try again.",
        );
      },
    });
  }

  /** Best effort: a denied or failed fix only means the controller sees an older position. */
  shareLocation(): void {
    this.location.current().then(
      (coordinates) =>
        this.api.reportLocation(coordinates).subscribe({
          next: (availability) => {
            this.availability.set(availability);
            this.locationState.set('shared');
          },
          error: () => undefined,
        }),
      () => this.locationState.set('blocked'),
    );
  }
}
