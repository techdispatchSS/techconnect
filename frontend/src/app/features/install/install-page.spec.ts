import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { InstallPlatform, InstallService } from '../../core/pwa/install.service';
import { InstallPage } from './install-page';

describe('InstallPage', () => {
  let fixture: ComponentFixture<InstallPage>;
  let el: HTMLElement;

  async function setup(platform: InstallPlatform, installed = false): Promise<void> {
    const installedSignal = signal(installed);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: InstallService,
          useValue: {
            platform,
            installed: installedSignal,
            available: () => !installedSignal(),
            canPrompt: () => false,
            install: () => Promise.resolve('manual'),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(InstallPage);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  it('offers the install button and the iPhone steps on an iPhone', async () => {
    await setup('ios');

    expect(el.querySelector('app-install-button button')).not.toBeNull();
    expect(el.textContent).toContain('Safari');
    expect(el.textContent).toContain('Add to Home Screen');
  });

  it('gives the browser-menu steps on Android', async () => {
    await setup('android');

    expect(el.textContent).toContain('Install app');
    expect(el.textContent).not.toContain('Safari');
  });

  it('says so, with no install button, when the app is already installed', async () => {
    await setup('android', true);

    expect(el.textContent).toContain('already installed');
    expect(el.querySelector('app-install-button')).toBeNull();
  });
});
