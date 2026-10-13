import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { AdminNavCountsService } from '../admin-nav-counts.service';
import { AdminUserService } from '../admin-user.service';
import { CreateUserRequest, CreateUserResponse } from '../admin.models';
import { UserCreate } from './user-create';

const CREATED = {
  user: { id: 'u9', name: 'Nomsa Khumalo', email: 'nomsa@example.com' },
  activationUrl: 'https://www.tech-connect.app/activate?token=abc',
} as unknown as CreateUserResponse;

describe('UserCreate (add-user steps)', () => {
  let fixture: ComponentFixture<UserCreate>;
  let el: HTMLElement;
  let create: ReturnType<typeof vi.fn>;

  async function setup(
    impl: (request: CreateUserRequest) => Observable<CreateUserResponse> = () => of(CREATED),
  ): Promise<void> {
    create = vi.fn(impl);
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: AdminUserService, useValue: { create } },
        { provide: AdminNavCountsService, useValue: { refresh: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(UserCreate);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function choose(selector: string, value: string): void {
    const select = el.querySelector<HTMLSelectElement>(selector)!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  async function press(label: RegExp): Promise<void> {
    const button = [...el.querySelectorAll<HTMLButtonElement>('.uc-form-footer button')].find((b) =>
      label.test(b.textContent ?? ''),
    )!;
    button.click();
    await fixture.whenStable();
  }

  function activeStep(): string {
    return el.querySelector('.uc-step--active .uc-step-label')!.textContent!.trim();
  }

  async function fillStepOne(): Promise<void> {
    type('#uc-first-name', 'Nomsa');
    type('#uc-last-name', 'Khumalo');
    type('#uc-email', 'nomsa@example.com');
    await fixture.whenStable();
  }

  async function fillStepTwo(): Promise<void> {
    type('#uc-street', '12 Long Street');
    type('#uc-suburb', 'Gardens');
    type('#uc-city', 'Cape Town');
    choose('#uc-province', 'WESTERN_CAPE');
    type('#uc-postal-code', '8001');
    await fixture.whenStable();
  }

  it('starts on the details step, without the role or address', async () => {
    await setup();

    expect(activeStep()).toBe('Details');
    expect(el.querySelector('#uc-first-name')).not.toBeNull();
    expect(el.querySelector('.uc-role-grid')).toBeNull();
    expect(el.querySelector('#uc-street')).toBeNull();
  });

  it('will not move on until the details are valid, and says what is missing', async () => {
    await setup();

    await press(/Next/);

    expect(activeStep()).toBe('Details');
    expect(el.textContent).toContain('First name is required');
    expect(el.textContent).toContain('Email is required');
  });

  it('asks for the role and address on the second step', async () => {
    await setup();
    await fillStepOne();

    await press(/Next/);

    expect(activeStep()).toBe('Role & access');
    expect(el.querySelectorAll('.uc-role-grid .admin-radio-card')).toHaveLength(3);
    expect(el.querySelector('#uc-street')).not.toBeNull();
    expect(el.querySelector('#uc-first-name')).toBeNull();
  });

  it('will not leave the second step with an incomplete address', async () => {
    await setup();
    await fillStepOne();
    await press(/Next/);

    await press(/Next/);

    expect(activeStep()).toBe('Role & access');
    expect(el.textContent).toContain('Street is required');
  });

  it('shows a review and sends nothing until the last step is confirmed', async () => {
    await setup();
    await fillStepOne();
    await press(/Next/);
    await fillStepTwo();
    await press(/Next/);

    expect(activeStep()).toBe('Review & send');
    expect(el.querySelector('.uc-review')!.textContent).toContain('Nomsa Khumalo');
    expect(el.querySelector('.uc-review')!.textContent).toContain(
      '12 Long Street, Gardens, Cape Town, Western Cape, 8001',
    );
    expect(create).not.toHaveBeenCalled();

    await press(/Add user and send invite/);

    expect(create).toHaveBeenCalledTimes(1);
    expect(create.mock.calls[0][0]).toMatchObject({
      firstName: 'Nomsa',
      lastName: 'Khumalo',
      email: 'nomsa@example.com',
      role: 'TECHNICIAN',
    });
    expect(el.textContent).toContain('has been added');
  });

  it('lets a completed step be reopened and keeps what was typed', async () => {
    await setup();
    await fillStepOne();
    await press(/Next/);

    el.querySelector<HTMLButtonElement>('.uc-step-btn')!.click();
    await fixture.whenStable();

    expect(activeStep()).toBe('Details');
    expect(el.querySelector<HTMLInputElement>('#uc-first-name')!.value).toBe('Nomsa');
  });

  it('returns to the details step when the email turns out to be taken', async () => {
    await setup(() => throwError(() => new HttpErrorResponse({ status: 409 })));
    await fillStepOne();
    await press(/Next/);
    await fillStepTwo();
    await press(/Next/);

    await press(/Add user and send invite/);

    expect(activeStep()).toBe('Details');
    expect(el.textContent).toContain('A user with that email address already exists');
  });
});
