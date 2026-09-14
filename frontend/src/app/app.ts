import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Root shell. Deliberately bare: the authenticated layout lives in {@code AppShell}, which
 * is itself a route, so the unauthenticated screens render full-bleed without a toolbar.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: '<router-outlet />',
})
export class App {}
