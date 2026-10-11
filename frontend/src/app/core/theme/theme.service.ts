import { Injectable, effect, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'techconnect.theme';

/**
 * One toggle for the whole app. `index.html` sets `data-theme` on `<html>` synchronously
 * before Angular bootstraps (avoiding a flash of the wrong theme on load); this service just
 * mirrors that same attribute afterwards and persists whatever the user picks.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<ThemeMode>(readCurrentAttribute());

  constructor() {
    effect(() => {
      const mode = this.theme();
      document.documentElement.setAttribute('data-theme', mode);
      try {
        localStorage.setItem(STORAGE_KEY, mode);
      } catch {
        // Private browsing / storage disabled — the choice just won't persist across reloads.
      }
    });
  }

  toggle(): void {
    this.theme.update((mode) => (mode === 'dark' ? 'light' : 'dark'));
  }
}

function readCurrentAttribute(): ThemeMode {
  const attr = document.documentElement.getAttribute('data-theme');
  return attr === 'dark' ? 'dark' : 'light';
}
