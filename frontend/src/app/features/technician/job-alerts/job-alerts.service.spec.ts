import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { TechnicianApiService } from '../technician-api.service';
import { PushConfig } from '../technician.models';
import { JobAlertsService } from './job-alerts.service';
import { PushBrowser } from './push-browser';

const CONFIG: PushConfig = {
  enabled: true,
  apiKey: 'k',
  projectId: 'p',
  messagingSenderId: 's',
  appId: 'a',
  vapidKey: 'v',
};

describe('JobAlertsService', () => {
  let api: {
    pushConfig: ReturnType<typeof vi.fn>;
    registerPushDevice: ReturnType<typeof vi.fn>;
    unregisterPushDevice: ReturnType<typeof vi.fn>;
  };
  let browser: {
    support: ReturnType<typeof vi.fn>;
    permission: ReturnType<typeof vi.fn>;
    requestPermission: ReturnType<typeof vi.fn>;
    getToken: ReturnType<typeof vi.fn>;
    deleteToken: ReturnType<typeof vi.fn>;
  };

  function setup(): JobAlertsService {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: TechnicianApiService, useValue: api },
        { provide: PushBrowser, useValue: browser },
      ],
    });
    return TestBed.inject(JobAlertsService);
  }

  beforeEach(() => {
    localStorage.clear();
    api = {
      pushConfig: vi.fn(() => of(CONFIG)),
      registerPushDevice: vi.fn(() => of(undefined)),
      unregisterPushDevice: vi.fn(() => of(undefined)),
    };
    browser = {
      support: vi.fn(() => 'supported'),
      permission: vi.fn(() => 'default'),
      requestPermission: vi.fn(() => Promise.resolve('granted')),
      getToken: vi.fn(() => Promise.resolve('the-token')),
      deleteToken: vi.fn(() => Promise.resolve()),
    };
  });

  afterEach(() => localStorage.clear());

  it('offers nothing until Firebase is set up', async () => {
    api.pushConfig.mockReturnValue(of({ enabled: false }));
    const service = setup();

    await service.start();

    expect(service.state()).toBe('hidden');
  });

  it('offers nothing if it cannot find out', async () => {
    api.pushConfig.mockReturnValue(throwError(() => new Error('offline')));
    const service = setup();

    await service.start();

    expect(service.state()).toBe('hidden');
  });

  it('says when an iPhone needs the app installed first, or the browser cannot do it', async () => {
    browser.support.mockReturnValue('needs-install');
    const service = setup();
    await service.start();
    expect(service.state()).toBe('needs-install');

    browser.support.mockReturnValue('unsupported');
    await service.start();
    expect(service.state()).toBe('unsupported');
  });

  it('says when notifications are blocked', async () => {
    browser.permission.mockReturnValue('denied');
    const service = setup();

    await service.start();

    expect(service.state()).toBe('blocked');
  });

  it('starts off, and does not ask for permission until tapped', async () => {
    const service = setup();

    await service.start();

    expect(service.state()).toBe('off');
    expect(browser.requestPermission).not.toHaveBeenCalled();
  });

  it('turns on by asking permission and registering the device', async () => {
    const service = setup();
    await service.start();

    await service.turnOn();

    expect(browser.getToken).toHaveBeenCalledWith(CONFIG);
    expect(api.registerPushDevice).toHaveBeenCalledWith('the-token');
    expect(service.state()).toBe('on');
  });

  it('stays off if they say not now, and shows blocked if they refuse', async () => {
    const service = setup();
    await service.start();

    browser.requestPermission.mockResolvedValue('default');
    await service.turnOn();
    expect(service.state()).toBe('off');

    browser.requestPermission.mockResolvedValue('denied');
    await service.turnOn();
    expect(service.state()).toBe('blocked');
    expect(api.registerPushDevice).not.toHaveBeenCalled();
  });

  it('says so if turning on fails, and stays off', async () => {
    browser.getToken.mockRejectedValue(new Error('no firebase'));
    const service = setup();
    await service.start();

    await service.turnOn();

    expect(service.state()).toBe('off');
    expect(service.error()).toContain("Couldn't turn on");
    expect(service.busy()).toBe(false);
  });

  it('re-registers a device that is already on when the app opens, as tokens change', async () => {
    browser.permission.mockReturnValue('granted');
    localStorage.setItem('techconnect.jobAlerts', 'on');
    const service = setup();

    await service.start();

    expect(service.state()).toBe('on');
    expect(api.registerPushDevice).toHaveBeenCalledWith('the-token');
  });

  it('stays off after being turned off, even though the browser still allows it', async () => {
    browser.permission.mockReturnValue('granted');
    localStorage.setItem('techconnect.jobAlerts', 'on');
    const service = setup();
    await service.start();

    await service.turnOff();

    expect(api.unregisterPushDevice).toHaveBeenCalledWith('the-token');
    expect(browser.deleteToken).toHaveBeenCalled();
    expect(service.state()).toBe('off');

    api.registerPushDevice.mockClear();
    await service.start();
    expect(service.state()).toBe('off');
    expect(api.registerPushDevice).not.toHaveBeenCalled();
  });

  it('stops alerts on this device when signing out', async () => {
    browser.permission.mockReturnValue('granted');
    localStorage.setItem('techconnect.jobAlerts', 'on');
    const service = setup();
    await service.start();

    await service.beforeSignOut();

    expect(api.unregisterPushDevice).toHaveBeenCalledWith('the-token');
    expect(localStorage.getItem('techconnect.jobAlerts')).toBeNull();
  });

  it('does not hold up signing out when it cannot reach the server', async () => {
    browser.permission.mockReturnValue('granted');
    localStorage.setItem('techconnect.jobAlerts', 'on');
    api.unregisterPushDevice.mockReturnValue(throwError(() => new Error('offline')));
    const service = setup();
    await service.start();

    await expect(service.beforeSignOut()).resolves.toBeUndefined();
  });

  it('has nothing to do on sign out if alerts were never on', async () => {
    const service = setup();
    await service.start();

    await service.beforeSignOut();

    expect(browser.getToken).not.toHaveBeenCalled();
  });
});
