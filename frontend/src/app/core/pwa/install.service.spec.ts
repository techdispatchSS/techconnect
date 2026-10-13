import { TestBed } from '@angular/core/testing';

import { InstallService } from './install.service';

function promptEvent(outcome: 'accepted' | 'dismissed'): Event {
  const event = new Event('beforeinstallprompt', { cancelable: true });
  Object.assign(event, {
    prompt: vi.fn(() => Promise.resolve()),
    userChoice: Promise.resolve({ outcome }),
  });
  return event;
}

describe('InstallService', () => {
  beforeEach(() => {
    delete window.__tcInstallPrompt;
  });

  it('has nothing to prompt with until the browser offers to install', async () => {
    const service = TestBed.inject(InstallService);

    expect(service.canPrompt()).toBe(false);
    expect(service.available()).toBe(true);
    // Without a prompt the caller is told to show manual steps.
    await expect(service.install()).resolves.toBe('manual');
  });

  it('keeps the browser prompt and uses it when asked to install', async () => {
    const service = TestBed.inject(InstallService);
    const event = promptEvent('accepted');

    window.dispatchEvent(event);

    // The browser's own mini-bar is suppressed so the app can offer its button instead.
    expect(event.defaultPrevented).toBe(true);
    expect(service.canPrompt()).toBe(true);

    await expect(service.install()).resolves.toBe('accepted');
    expect((event as unknown as { prompt: () => void }).prompt).toHaveBeenCalled();
    expect(service.installed()).toBe(true);
    expect(service.available()).toBe(false);
  });

  it('can only use a prompt once, and stays available if it was dismissed', async () => {
    const service = TestBed.inject(InstallService);
    window.dispatchEvent(promptEvent('dismissed'));

    await expect(service.install()).resolves.toBe('dismissed');

    expect(service.canPrompt()).toBe(false);
    expect(service.installed()).toBe(false);
    expect(service.available()).toBe(true);
  });

  it('stops offering the button once the app has been installed', () => {
    const service = TestBed.inject(InstallService);

    window.dispatchEvent(new Event('appinstalled'));

    expect(service.available()).toBe(false);
  });
});
