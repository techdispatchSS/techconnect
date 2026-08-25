import { Routes } from '@angular/router';

/** Admin portal (M-06). Reached only through the MANAGER-guarded parent route. */
export const adminRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'users' },
  {
    path: 'users',
    loadComponent: () => import('./user-list/user-list').then((m) => m.UserList),
    title: 'Users · TechDispatch',
  },
  {
    path: 'users/:id',
    loadComponent: () => import('./user-detail/user-detail').then((m) => m.UserDetail),
    title: 'User · TechDispatch',
  },
  {
    path: 'audit',
    loadComponent: () => import('./audit-list/audit-list').then((m) => m.AuditList),
    title: 'Audit trail · TechDispatch',
  },
];
