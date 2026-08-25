import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // --- Public: the only ways into the system ---
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
    title: 'Sign in · TechDispatch',
  },
  {
    path: 'activate',
    loadComponent: () =>
      import('./features/auth/set-password/set-password').then((m) => m.SetPassword),
    data: { mode: 'ACTIVATE' },
    title: 'Activate your account · TechDispatch',
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/set-password/set-password').then((m) => m.SetPassword),
    data: { mode: 'RESET' },
    title: 'Reset your password · TechDispatch',
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
    title: 'Forgot your password · TechDispatch',
  },

  // --- Authenticated: everything inside the application shell ---
  {
    path: '',
    loadComponent: () => import('./layout/app-shell/app-shell').then((m) => m.AppShell),
    canActivate: [authGuard],
    children: [
      {
        path: 'admin',
        // A usability guard only — the backend enforces the same rule on every request.
        canActivate: [roleGuard],
        data: { roles: ['MANAGER'] },
        loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
      },
      {
        path: 'forbidden',
        loadComponent: () =>
          import('./features/errors/forbidden/forbidden').then((m) => m.Forbidden),
        title: 'Not available · TechDispatch',
      },
      // Root redirects by role, which is resolved at navigation time rather than baked in.
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/errors/role-redirect/role-redirect').then((m) => m.RoleRedirect),
      },
    ],
  },

  { path: '**', redirectTo: '' },
];
