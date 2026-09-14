import { Routes } from '@angular/router';

/** Admin portal (M-06). Reached only through the MANAGER-guarded `admin` route, which
 * renders `AdminShell` around every route below. */
export const adminRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  {
    path: 'users',
    loadComponent: () => import('./user-list/user-list').then((m) => m.UserList),
    title: 'Users · TechConnect',
    data: { heading: 'People directory' },
  },
  {
    path: 'users/new',
    loadComponent: () => import('./user-create/user-create').then((m) => m.UserCreate),
    title: 'Add a user · TechConnect',
    data: { heading: 'Add a user' },
  },
  {
    path: 'invites',
    loadComponent: () => import('./invites/invites').then((m) => m.Invites),
    title: 'Invites · TechConnect',
    data: { heading: 'Invites & activation' },
  },
  {
    path: 'audit',
    loadComponent: () => import('./audit-list/audit-list').then((m) => m.AuditList),
    title: 'Audit trail · TechConnect',
    data: { heading: 'Audit trail' },
  },
];
