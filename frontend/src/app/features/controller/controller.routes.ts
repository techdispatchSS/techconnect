import { Routes } from '@angular/router';

/** Controller dashboard (FR-02–FR-04). Reached only through the CONTROLLER-guarded
 * `incidents` route, which renders `ControllerShell` around every route below. */
export const controllerRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'queue' },
  {
    path: 'queue',
    loadComponent: () => import('./incident-queue/incident-queue').then((m) => m.IncidentQueue),
    title: 'Incident queue · TechConnect',
    data: { heading: 'Incident queue' },
  },
  {
    path: 'new',
    loadComponent: () => import('./incident-create/incident-create').then((m) => m.IncidentCreate),
    title: 'Log incident · TechConnect',
    data: { heading: 'Log incident' },
  },
  {
    path: 'dispatch/:incidentId',
    loadComponent: () => import('./create-dispatch/create-dispatch').then((m) => m.CreateDispatch),
    title: 'Create dispatch · TechConnect',
    data: { heading: 'Create dispatch' },
  },
  {
    path: 'jobs',
    loadComponent: () => import('./job-list/job-list').then((m) => m.JobList),
    title: 'Active jobs · TechConnect',
    data: {
      heading: 'Active jobs',
      stages: ['ACTIVE'],
      intro: 'Jobs a technician has accepted and is working, most urgent first.',
    },
  },
  {
    path: 'jobs/:jobId',
    loadComponent: () => import('./job-detail/job-detail').then((m) => m.JobDetailPage),
    title: 'Job · TechConnect',
    data: { heading: 'Job' },
  },
  {
    path: 'review',
    loadComponent: () => import('./job-list/job-list').then((m) => m.JobList),
    title: 'Review & close · TechConnect',
    data: {
      heading: 'Review & close',
      stages: ['REVIEW', 'CLOSED'],
      intro: 'Technician reports waiting for your sign-off, oldest SLA first.',
    },
  },
  {
    path: 'technicians',
    loadComponent: () =>
      import('./technician-roster/technician-roster').then((m) => m.TechnicianRoster),
    title: 'Technicians · TechConnect',
    data: { heading: 'Technicians' },
  },
  {
    path: 'reports',
    loadComponent: () => import('./reports/reports').then((m) => m.Reports),
    title: 'Reports · TechConnect',
    data: { heading: 'Reports' },
  },
  {
    path: 'progress/:incidentId',
    loadComponent: () => import('./ticket-progress/ticket-progress').then((m) => m.TicketProgress),
    title: 'Ticket progress · TechConnect',
    data: { heading: 'Ticket progress' },
  },
];
