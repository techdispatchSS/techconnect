import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { JobAlerts } from './job-alerts';
import { JobAlertsService, JobAlertsState } from './job-alerts.service';

describe('JobAlerts', () => {
  const state = signal<JobAlertsState>('hidden');
  const error = signal<string | null>(null);
  let turnOn: ReturnType<typeof vi.fn>;
  let turnOff: ReturnType<typeof vi.fn>;

  function setup(start: JobAlertsState): ComponentFixture<JobAlerts> {
    state.set(start);
    error.set(null);
    turnOn = vi.fn();
    turnOff = vi.fn();
    TestBed.configureTestingModule({
      imports: [JobAlerts],
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        {
          provide: JobAlertsService,
          useValue: { state, error, busy: signal(false), turnOn, turnOff },
        },
      ],
    });
    const fixture = TestBed.createComponent(JobAlerts);
    fixture.detectChanges();
    return fixture;
  }

  function el(fixture: ComponentFixture<JobAlerts>): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('shows nothing when alerts are not available', () => {
    expect(el(setup('hidden')).querySelector('.ja-card')).toBeNull();
  });

  it('offers to turn them on', () => {
    const fixture = setup('off');

    el(fixture).querySelector('button')!.click();

    expect(turnOn).toHaveBeenCalled();
  });

  it('offers to turn them off once on', () => {
    const fixture = setup('on');
    expect(el(fixture).textContent).toContain('Job alerts are on');

    el(fixture).querySelector('button')!.click();

    expect(turnOff).toHaveBeenCalled();
  });

  it('points an iPhone at installing the app', () => {
    const link = el(setup('needs-install')).querySelector('a');

    expect(link?.getAttribute('href')).toBe('/install');
  });

  it('explains being blocked, or a browser that cannot, and that email still works', () => {
    expect(el(setup('blocked')).textContent).toContain('still come by email');
    TestBed.resetTestingModule();
    expect(el(setup('unsupported')).textContent).toContain('still come by email');
  });

  it('shows what went wrong', () => {
    const fixture = setup('off');

    error.set("Couldn't turn on job alerts.");
    fixture.detectChanges();

    expect(el(fixture).querySelector('[role="alert"]')?.textContent).toContain("Couldn't turn on");
  });
});
