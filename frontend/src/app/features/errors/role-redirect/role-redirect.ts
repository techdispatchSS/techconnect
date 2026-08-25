import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../../core/auth/auth.service';

/**
 * Lands the user on the right home screen for their role.
 *
 * A static `redirectTo` cannot express this because the destination depends on who is
 * signed in, which is not known until the navigation actually happens.
 */
@Component({
  selector: 'app-role-redirect',
  template: '',
})
export class RoleRedirect {
  constructor() {
    const auth = inject(AuthService);
    const router = inject(Router);
    void router.navigateByUrl(auth.homeRouteFor(auth.role()));
  }
}
