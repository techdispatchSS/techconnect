import { Injectable, computed, signal } from '@angular/core';

/** Chromium's install prompt event; not in the DOM lib typings. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

declare global {
  interface Window {
    /** Set by the inline script in index.html, which listens before Angular has started. */
    __tcInstallPrompt?: BeforeInstallPromptEvent;
  }
}

export type InstallPlatform = 'ios' | 'android' | 'desktop';

export type InstallOutcome = 'accepted' | 'dismissed' | 'manual';

/**
 * Lets a person put TechConnect on their phone (or desktop) as an installed app.
 *
 * <p>Chromium browsers hand the page an install prompt that can be triggered from a button; this
 * service keeps hold of it. Safari on iOS has no such prompt, and nor does a browser that has
 * not yet decided the site is installable, so for those {@link install} reports `'manual'` and
 * the caller shows the steps instead.
 */
@Injectable({ providedIn: 'root' })
export class InstallService {
  private readonly deferred = signal<BeforeInstallPromptEvent | null>(
    typeof window === 'undefined' ? null : (window.__tcInstallPrompt ?? null),
  );

  /** True once the app is running installed (or has just been installed from this page). */
  readonly installed = signal(isStandalone());

  readonly platform: InstallPlatform = detectPlatform();

  /** Whether the browser will show its own install dialog when asked. */
  readonly canPrompt = computed(() => this.deferred() !== null);

  /** Whether to offer an install button at all: not when the app is already installed. */
  readonly available = computed(() => !this.installed());

  constructor() {
    if (typeof window === 'undefined') {
      return;
    }
    window.addEventListener('beforeinstallprompt', (event) => {
      event.preventDefault();
      this.deferred.set(event as BeforeInstallPromptEvent);
    });
    window.addEventListener('appinstalled', () => {
      this.deferred.set(null);
      this.installed.set(true);
    });
  }

  async install(): Promise<InstallOutcome> {
    const prompt = this.deferred();
    if (!prompt) {
      return 'manual';
    }
    await prompt.prompt();
    const choice = await prompt.userChoice;
    // A prompt can only be used once, whatever the person chose.
    this.deferred.set(null);
    if (choice.outcome === 'accepted') {
      this.installed.set(true);
    }
    return choice.outcome;
  }
}

function isStandalone(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
  return iosStandalone || (window.matchMedia?.('(display-mode: standalone)').matches ?? false);
}

function detectPlatform(): InstallPlatform {
  if (typeof navigator === 'undefined') {
    return 'desktop';
  }
  const ua = navigator.userAgent;
  // iPadOS 13+ reports itself as a Mac, so tell it apart by touch support.
  const ipadAsMac = /Macintosh/.test(ua) && navigator.maxTouchPoints > 1;
  if (/iPhone|iPad|iPod/.test(ua) || ipadAsMac) {
    return 'ios';
  }
  return /Android/.test(ua) ? 'android' : 'desktop';
}
