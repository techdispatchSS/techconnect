import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

/** Shown when a signed-in user reaches an area their role does not cover (FR-01). */
@Component({
  selector: 'app-forbidden',
  imports: [MatButtonModule, MatCardModule],
  templateUrl: './forbidden.html',
  styleUrl: '../../auth/auth-page.scss',
})
export class Forbidden {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly role = this.auth.role;

  goHome(): void {
    void this.router.navigateByUrl(this.auth.homeRouteFor(this.auth.role()));
  }
}
