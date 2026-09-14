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
    path: 'dispatch/:incidentId',
    loadComponent: () =>
      import('./create-dispatch/create-dispatch').then((m) => m.CreateDispatch),
    title: 'Create dispatch · TechConnect',
    data: { heading: 'Create dispatch' },
  },
];
