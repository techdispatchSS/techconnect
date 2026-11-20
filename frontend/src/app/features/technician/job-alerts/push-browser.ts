import { Injectable, inject } from '@angular/core';

import { InstallService } from '../../../core/pwa/install.service';
import { PushConfig } from '../technician.models';

export type PushSupport = 'supported' | 'needs-install' | 'unsupported';

const SERVICE_WORKER = '/push-sw.js';

/**
 * The browser's and Firebase's side of job alerts, kept apart so the rest can be tested without
 * either. The native apps (#70, #71) will provide their own version of this. Firebase is only
 * loaded by someone actually using alerts, as it's large and most pages never need it.
 */
@Injectable({ providedIn: 'root' })
export class PushBrowser {
  private readonly install = inject(InstallService);

  support(): PushSupport {
    const available =
      'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
    if (available) {
      return 'supported';
    }
    // An iPhone only offers push to an app added to the Home Screen.
    return this.install.platform === 'ios' && !this.install.installed()
      ? 'needs-install'
      : 'unsupported';
  }

  permission(): NotificationPermission {
    return Notification.permission;
  }

  requestPermission(): Promise<NotificationPermission> {
    return Notification.requestPermission();
  }

  async getToken(config: PushConfig): Promise<string> {
    const { getToken } = await import('@firebase/messaging');
    const registration = await navigator.serviceWorker.register(SERVICE_WORKER);
    return getToken(await this.messaging(config), {
      vapidKey: config.vapidKey,
      serviceWorkerRegistration: registration,
    });
  }

  async deleteToken(config: PushConfig): Promise<void> {
    const { deleteToken } = await import('@firebase/messaging');
    await deleteToken(await this.messaging(config));
  }

  private async messaging(config: PushConfig) {
    const [{ getApps, initializeApp }, { getMessaging }] = await Promise.all([
      import('@firebase/app'),
      import('@firebase/messaging'),
    ]);
    const app =
      getApps()[0] ??
      initializeApp({
        apiKey: config.apiKey,
        projectId: config.projectId,
        messagingSenderId: config.messagingSenderId,
        appId: config.appId,
      });
    return getMessaging(app);
  }
}
