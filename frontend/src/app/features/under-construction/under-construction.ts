import { Component, computed, inject } from '@angular/core';

import { UserRole } from '../../core/auth/auth.models';
import { AuthService } from '../../core/auth/auth.service';

interface WaitingCopy {
  readonly kicker: string;
  readonly title: string;
  readonly lead: string;
  readonly upcoming: readonly { readonly heading: string; readonly detail: string }[];
}

const COPY: Partial<Record<UserRole, WaitingCopy>> = {
  TECHNICIAN: {
    kicker: 'Technician app · coming soon',
    title: 'Your jobs are on the way',
    lead:
      "We're still building the technician app. Your account is set up and ready. " +
      "There's nothing you need to do until it launches.",
    upcoming: [
      { heading: 'Job offers', detail: 'See new jobs near you and accept in one tap.' },
      {
        heading: 'Job details & directions',
        detail: 'Site contact, notes and turn-by-turn navigation.',
      },
      {
        heading: 'Live status updates',
        detail: 'Mark yourself en route, on site and done from your phone.',
      },
    ],
  },
  CONTROLLER: {
    kicker: 'Dispatch board · coming soon',
    title: 'Your dispatch board is being built',
    lead:
      "We're still building the controller workspace. Your account is set up and ready. " +
      "There's nothing you need to do until it launches.",
    upcoming: [
      { heading: 'Incident queue', detail: 'Every open incident, prioritised by SLA and urgency.' },
      {
        heading: 'Smart dispatch',
        detail: 'Match the right technician by skill, availability and distance.',
      },
      {
        heading: 'Live job tracking',
        detail: 'Follow each job from dispatch through to sign-off.',
      },
    ],
  },
};

const FALLBACK: WaitingCopy = {
  kicker: 'Coming soon',
  title: 'This part of TechConnect is being built',
  lead: "We're still working on this area. Your account is set up and ready.",
  upcoming: [],
};

/**
 * Holding page for the Controller and Technician homes, which don't exist yet. Both roles land
 * here after sign-in instead of on a redirect loop, with copy that says what's coming for
 * their role. Swap the routes for the real screens when they ship; nothing else depends on
 * this component.
 */
@Component({
  selector: 'app-under-construction',
  templateUrl: './under-construction.html',
  styleUrl: './under-construction.scss',
})
export class UnderConstruction {
  private readonly auth = inject(AuthService);

  readonly name = this.auth.displayName;
  readonly copy = computed(() => {
    const role = this.auth.role();
    return (role && COPY[role]) || FALLBACK;
  });
  readonly supportHref = 'mailto:support@tech-connect.app?subject=TechConnect%20launch%20question';
}
