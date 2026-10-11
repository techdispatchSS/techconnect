import { DestroyRef, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { ControllerApiService } from './controller-api.service';
import { FreshdeskStatus } from './controller.models';
import { FreshdeskStatusService } from './freshdesk-status.service';

const STATUS: FreshdeskStatus = {
  mode: 'NOT_CONNECTED',
  simulation: false,
  lastSyncAt: null,
  lastSuccessAt: null,
  lastError: null,
};

describe('FreshdeskStatusService', () => {
  function setup(freshdeskStatus: () => unknown) {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: ControllerApiService, useValue: { freshdeskStatus } },
      ],
    });
    return TestBed.inject(FreshdeskStatusService);
  }

  it('reports what the backend says, and nothing before it has answered', () => {
    const service = setup(() => of(STATUS));
    expect(service.status()).toBeNull();

    service.start(TestBed.inject(DestroyRef));

    expect(service.status()).toEqual(STATUS);
  });

  it('keeps the last known status when a poll fails, instead of claiming nothing', () => {
    let calls = 0;
    const service = setup(() => (calls++ === 0 ? of(STATUS) : throwError(() => new Error('down'))));
    service.start(TestBed.inject(DestroyRef));
    expect(service.status()).toEqual(STATUS);

    service.refresh();

    expect(service.status()).toEqual(STATUS);
  });
});
