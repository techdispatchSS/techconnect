import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { TechnicianApiService } from '../technician-api.service';
import { PushConfig } from '../technician.models';
import { PushBrowser } from './push-browser';

export type JobAlertsState =
  /** Not known yet, or Firebase isn't set up: nothing to offer. */
  'hidden' | 'unsupported' | 'needs-install' | 'blocked' | 'off' | 'on';

/** Remembers that alerts were turned on here: the browser's permission outlives turning them off. */
const STORAGE_KEY = 'techconnect.jobAlerts';
/** Signing out shouldn't hang on a poor connection. */
const SIGN_OUT_WAIT_MS = 2_000;

function remembered(): boolean {
  try {
    return localStorage.getItem(STORAGE_KEY) === 'on';
  } catch {
    return false;
  }
}

function remember(on: boolean): void {
  try {
    if (on) {
      localStorage.setItem(STORAGE_KEY, 'on');
    } else {
      localStorage.removeItem(STORAGE_KEY);
    }
  } catch {
    // Private mode: alerts still work until this page closes.
  }
}

/**
 * Whether this device gets job alerts, and turning them on and off (#69). Started by the shell,
 * since tokens change: an already-on device re-registers each time the app opens.
 */
@Injectable({ providedIn: 'root' })
export class JobAlertsService {
  private readonly api = inject(TechnicianApiService);
  private readonly browser = inject(PushBrowser);

  readonly state = signal<JobAlertsState>('hidden');
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  private config: PushConfig | null = null;

  async start(): Promise<void> {
    this.state.set('hidden');
    this.error.set(null);
    try {
      this.config = await firstValueFrom(this.api.pushConfig());
    } catch {
      return;
    }
    if (!this.config.enabled) {
      return;
    }
    const support = this.browser.support();
    if (support !== 'supported') {
      this.state.set(support);
      return;
    }
    const permission = this.browser.permission();
    if (permission === 'denied') {
      this.state.set('blocked');
      return;
    }
    if (permission !== 'granted' || !remembered()) {
      this.state.set('off');
      return;
    }
    this.state.set('on');
    try {
      await this.register();
    } catch {
      // Still on as far as they're concerned; the next open tries again.
    }
  }

  /** From a tap, as iPhones only allow the permission prompt in response to one. */
  async turnOn(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      const permission = await this.browser.requestPermission();
      if (permission === 'granted') {
        await this.register();
        remember(true);
        this.state.set('on');
      } else if (permission === 'denied') {
        this.state.set('blocked');
      }
    } catch {
      this.error.set("Couldn't turn on job alerts. Check your connection and try again.");
    } finally {
      this.busy.set(false);
    }
  }

  async turnOff(): Promise<void> {
    if (this.busy()) {
      return;
    }
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.unregister();
      remember(false);
      this.state.set('off');
    } catch {
      this.error.set("Couldn't turn off job alerts. Check your connection and try again.");
    } finally {
      this.busy.set(false);
    }
  }

  /**
   * Before the token is gone: this device must stop showing their offers. Never blocks the sign
   * out, and stays remembered if it couldn't reach the server, so the next sign-in here takes the
   * device over instead.
   */
  async beforeSignOut(): Promise<void> {
    if (this.state() !== 'on') {
      return;
    }
    const done = this.unregister().then(
      () => remember(false),
      () => undefined,
    );
    await Promise.race([
      done,
      new Promise<void>((resolve) => setTimeout(resolve, SIGN_OUT_WAIT_MS)),
    ]);
    this.state.set('hidden');
  }

  private async register(): Promise<void> {
    const token = await this.browser.getToken(this.config!);
    await firstValueFrom(this.api.registerPushDevice(token));
  }

  private async unregister(): Promise<void> {
    const token = await this.browser.getToken(this.config!);
    await firstValueFrom(this.api.unregisterPushDevice(token));
    // Best effort: the server has already forgotten it.
    await this.browser.deleteToken(this.config!).catch(() => undefined);
  }
}
