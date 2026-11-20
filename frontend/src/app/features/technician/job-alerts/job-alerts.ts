import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { JobAlertsService } from './job-alerts.service';

/** Turning job alerts on or off for this device, on the technician's home (#69). */
@Component({
  selector: 'app-job-alerts',
  imports: [RouterLink],
  templateUrl: './job-alerts.html',
  styleUrl: './job-alerts.scss',
})
export class JobAlerts {
  readonly alerts = inject(JobAlertsService);
}
