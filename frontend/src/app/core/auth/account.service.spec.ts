import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { AccountService, BROWSER_TIME_ZONE, Profile } from './account.service';

function profile(timeZone: string | null): Profile {
  return {
    id: 'u1',
    email: 'thabo@example.com',
    name: 'Thabo',
    phone: null,
    address: null,
    role: 'TECHNICIAN',
    canEditAddress: false,
    timeZone,
  };
}

describe('AccountService time zone', () => {
  let service: AccountService;
  let http: HttpTestingController;

  let zone: string | null = null;

  /** Places the browser, before the service first asks where it is. */
  function browserIsIn(where: string | null | undefined): void {
    zone = where ?? null;
  }

  beforeEach(() => {
    zone = null;
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: BROWSER_TIME_ZONE, useValue: () => zone },
      ],
    });
    service = TestBed.inject(AccountService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('tells the backend the zone the browser is in when it differs from the record', () => {
    browserIsIn('Africa/Nairobi');

    service.me().subscribe();
    http.expectOne('v1/auth/me').flush(profile('Africa/Johannesburg'));

    const put = http.expectOne('v1/auth/me/time-zone');
    expect(put.request.method).toBe('PUT');
    expect(put.request.body).toEqual({ timeZone: 'Africa/Nairobi' });
    put.flush(profile('Africa/Nairobi'));
  });

  it('reports a first zone for someone who has none on record', () => {
    browserIsIn('Africa/Lagos');

    service.me().subscribe();
    http.expectOne('v1/auth/me').flush(profile(null));

    http.expectOne('v1/auth/me/time-zone').flush(profile('Africa/Lagos'));
  });

  it('says nothing when the record already matches', () => {
    browserIsIn('Africa/Johannesburg');

    service.me().subscribe();
    http.expectOne('v1/auth/me').flush(profile('Africa/Johannesburg'));

    http.expectNone('v1/auth/me/time-zone');
  });

  it('says nothing when the browser will not say where it is', () => {
    browserIsIn(undefined);

    service.me().subscribe();
    http.expectOne('v1/auth/me').flush(profile(null));

    http.expectNone('v1/auth/me/time-zone');
  });

  it('carries on if the report is refused', () => {
    browserIsIn('Africa/Accra');
    let result: Profile | undefined;

    service.me().subscribe((p) => (result = p));
    http.expectOne('v1/auth/me').flush(profile('Africa/Johannesburg'));
    http
      .expectOne('v1/auth/me/time-zone')
      .flush({ error: 'no' }, { status: 400, statusText: 'Bad Request' });

    expect(result?.id).toBe('u1');
  });
});
