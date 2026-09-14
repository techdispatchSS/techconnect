import { Routes } from '@angular/router';

import { authGuard, roleGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  // --- Public: the only ways into the system ---
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
    title: 'Sign in · TechConnect',
  },
  {
    path: 'activate',
    loadComponent: () =>
      import('./features/auth/set-password/set-password').then((m) => m.SetPassword),
    data: { mode: 'ACTIVATE' },
    title: 'Activate your account · TechConnect',
  },
  {
    path: 'reset-password',
    loadComponent: () =>
      import('./features/auth/set-password/set-password').then((m) => m.SetPassword),
    data: { mode: 'RESET' },
    title: 'Reset your password · TechConnect',
  },
  {
    path: 'forgot-password',
    loadComponent: () =>
      import('./features/auth/forgot-password/forgot-password').then((m) => m.ForgotPassword),
    title: 'Forgot your password · TechConnect',
  },

  // --- Admin portal: its own dedicated shell (Manager-only), not nested inside the
  // generic AppShell — see admin-shell.ts. ---
  {
    path: 'admin',
    loadComponent: () => import('./features/admin/admin-shell/admin-shell').then((m) => m.AdminShell),
    // A usability guard only — the backend enforces the same rule on every request.
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MANAGER'] },
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
  },

  // --- Controller dashboard: its own dedicated shell (Controller-only), matching the
  // design mockup's dark "Industry" theme rather than the generic AppShell. ---
  {
    path: 'incidents',
    loadComponent: () =>
      import('./features/controller/controller-shell/controller-shell').then(
        (m) => m.ControllerShell,
      ),
    canActivate: [authGuard, roleGuard],
    data: { roles: ['CONTROLLER'] },
    loadChildren: () => import('./features/controller/controller.routes').then((m) => m.controllerRoutes),
  },

  // --- Authenticated: everything else inside the application shell ---
  {
    path: '',
    loadComponent: () => import('./layout/app-shell/app-shell').then((m) => m.AppShell),
    canActivate: [authGuard],
    children: [
      {
        path: 'forbidden',
        loadComponent: () =>
          import('./features/errors/forbidden/forbidden').then((m) => m.Forbidden),
        title: 'Not available · TechConnect',
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
