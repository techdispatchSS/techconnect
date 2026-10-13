import { HttpErrorResponse } from '@angular/common/http';
import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { GooglePlacesService } from '../../core/google/google-places.service';
import { PickedPlace, StreetInput } from '../../shared/street-input/street-input';
import { ApplyApiService } from './apply-api.service';
import { Apply } from './apply';
import { ApplicationReceivedResponse, SubmitApplicationRequest } from './apply.models';

const RECEIVED: ApplicationReceivedResponse = { message: 'Thank you. We have it.' };

function pdf(name = 'ccna.pdf'): File {
  return new File(['%PDF-1.4 test'], name, { type: 'application/pdf' });
}

describe('Apply', () => {
  let fixture: ComponentFixture<Apply>;
  let el: HTMLElement;
  let submit: ReturnType<typeof vi.fn>;

  async function setup(
    impl: () => Observable<ApplicationReceivedResponse> = () => of(RECEIVED),
  ): Promise<void> {
    submit = vi.fn(impl);
    window.scrollTo = vi.fn();

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ApplyApiService, useValue: { submit } },
      ],
    });

    fixture = TestBed.createComponent(Apply);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  function type(selector: string, value: string): void {
    const input = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function choose(selector: string, value: string): void {
    const select = el.querySelector<HTMLSelectElement>(selector)!;
    select.value = value;
    select.dispatchEvent(new Event('change'));
  }

  function fillValidForm(): void {
    type('#ap-first', 'Nomsa');
    type('#ap-last', 'Khumalo');
    type('#ap-email', 'nomsa@example.com');
    type('#ap-phone', '082 123 4567');
    type('#ap-id', '9001015000085');
    type('#ap-street', '1 Test Street');
    type('#ap-suburb', 'Umhlanga');
    type('#ap-city', 'Durban');
    choose('#ap-province', 'KWAZULU_NATAL');
    type('#ap-postal', '4001');
    type('#ap-experience', 'Eight years installing network and CCTV systems for businesses.');
    (el.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
  }

  async function attach(...files: File[]): Promise<void> {
    const input = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(input, 'files', { value: files, configurable: true });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function send(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('sends nothing for an empty form and says proof is required', async () => {
    await setup();
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Attach at least one certificate or qualification');
    expect(el.textContent).toContain('First name is required');
  });

  it('will not send without any proof attached, even if every field is valid', async () => {
    await setup();
    fillValidForm();
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Attach at least one certificate or qualification');
  });

  it('will not send an ID number with a bad checksum, and says why', async () => {
    await setup();
    fillValidForm();
    type('#ap-id', '9001015000084');
    await attach(pdf());
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Enter a valid 13-digit South African ID number');
  });

  it('requires an ID number', async () => {
    await setup();
    fillValidForm();
    type('#ap-id', '');
    await attach(pdf());
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('ID number is required');
  });

  it('accepts an ID number typed with spaces and sends it as plain digits', async () => {
    await setup();
    fillValidForm();
    type('#ap-id', '900101 5000 085');
    await attach(pdf());
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
    const [request] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
    expect(request.idNumber).toBe('9001015000085');
  });

  it('will not send without agreeing to the privacy notice', async () => {
    await setup();
    fillValidForm();
    (el.querySelector('input[type="checkbox"]') as HTMLInputElement).click(); // untick
    await attach(pdf());
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('You need to agree before you can apply');
  });

  it('refuses disallowed and oversize files with a reason, and keeps the good ones', async () => {
    await setup();
    const big = new File(['x'], 'huge.pdf');
    Object.defineProperty(big, 'size', { value: 11 * 1024 * 1024 });

    await attach(new File(['MZ'], 'virus.exe'), big, pdf('good.pdf'));

    expect(el.textContent).toContain('virus.exe');
    expect(el.textContent).toContain("isn't a PDF, JPG or PNG");
    expect(el.textContent).toContain('huge.pdf');
    expect(el.textContent).toContain('the limit is');
    expect(el.querySelectorAll('.ap-file')).toHaveLength(1);
    expect(el.querySelector('.ap-file-name')!.textContent).toContain('good.pdf');
  });

  it('caps the number of attachments', async () => {
    await setup();
    await attach(...Array.from({ length: 12 }, (_, i) => pdf(`c${i}.pdf`)));

    expect(el.querySelectorAll('.ap-file')).toHaveLength(10);
    expect(el.textContent).toContain('at most 10 files');
  });

  it('lets a file be removed again', async () => {
    await setup();
    await attach(pdf('a.pdf'), pdf('b.pdf'));
    expect(el.querySelectorAll('.ap-file')).toHaveLength(2);

    (el.querySelector('.ap-file-remove') as HTMLButtonElement).click();
    await fixture.whenStable();

    expect(el.querySelectorAll('.ap-file')).toHaveLength(1);
    expect(el.querySelector('.ap-file-name')!.textContent).toContain('b.pdf');
  });

  it('submits the details and every file, with trimmed values and the chosen document type', async () => {
    await setup();
    fillValidForm();
    type('#ap-first', '  Nomsa  ');
    await attach(pdf('ccna.pdf'));
    choose('select[id^="ap-doc-kind-"]', 'QUALIFICATION');
    type('input[id^="ap-doc-title-"]', '  CCNA Routing  ');
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
    const [request, files] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
    expect(request.firstName).toBe('Nomsa');
    expect(request.address).toEqual({
      street: '1 Test Street',
      suburb: 'Umhlanga',
      city: 'Durban',
      province: 'KWAZULU_NATAL',
      postalCode: '4001',
    });
    expect(request.documents).toEqual([{ kind: 'QUALIFICATION', title: 'CCNA Routing' }]);
    expect(request.consent).toBe(true);
    expect(request.website).toBe('');
    expect(files.map((f) => f.name)).toEqual(['ccna.pdf']);
  });

  it('shows the confirmation instead of the form once accepted', async () => {
    await setup();
    fillValidForm();
    await attach(pdf());
    await send();

    expect(el.querySelector('form')).toBeNull();
    expect(el.textContent).toContain('Thank you. We have it.');
  });

  it("shows the server's own message when the files are refused", async () => {
    await setup(() =>
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: { error: '"cert.pdf" contains scripts, embedded files or encryption.' },
          }),
      ),
    );
    fillValidForm();
    await attach(pdf());
    await send();

    expect(el.querySelector('[role="alert"].auth-error')!.textContent).toContain(
      'contains scripts',
    );
    expect(el.querySelector('form')).not.toBeNull();
  });

  it('explains a rate limit rather than failing silently', async () => {
    await setup(() => throwError(() => new HttpErrorResponse({ status: 429, error: {} })));
    fillValidForm();
    await attach(pdf());
    await send();

    expect(el.textContent).toContain('Too many applications');
  });

  it('does not send twice while a submission is in flight', async () => {
    const { Subject } = await import('rxjs');
    const pending = new Subject<ApplicationReceivedResponse>();
    await setup(() => pending);
    fillValidForm();
    await attach(pdf());

    await send();
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('keeps the honeypot out of sight of people and assistive tech', async () => {
    await setup();
    const honeypot = el.querySelector('.ap-hp')!;
    expect(honeypot.getAttribute('aria-hidden')).toBe('true');
    expect(honeypot.querySelector('input')!.getAttribute('tabindex')).toBe('-1');
  });
});

const SANDTON: PickedPlace = {
  street: '83 Rivonia Road',
  suburb: 'Sandown',
  city: 'Sandton',
  province: 'GAUTENG',
  postalCode: '2196',
  latitude: -26.1,
  longitude: 28.05,
};

describe('Apply: address search', () => {
  let fixture: ComponentFixture<Apply>;
  let el: HTMLElement;

  async function setup(placesEnabled: boolean): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ApplyApiService, useValue: { submit: vi.fn() } },
        {
          provide: GooglePlacesService,
          useValue: { enabled: placesEnabled, suggest: () => Promise.resolve([]) },
        },
      ],
    });
    fixture = TestBed.createComponent(Apply);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  function pick(place: PickedPlace): void {
    fixture.debugElement.query(By.directive(StreetInput)).componentInstance.picked.emit(place);
  }

  it('offers the street search where the address is asked for', async () => {
    await setup(true);

    const street = el.querySelector<HTMLInputElement>('app-street-input input#ap-street');
    expect(street).not.toBeNull();
    expect(street!.getAttribute('role')).toBe('combobox');
    expect(el.textContent).toContain(
      "Choose your address from the list and we'll fill in the rest.",
    );
  });

  it('fills the whole address when a suggestion is picked', async () => {
    await setup(true);

    pick(SANDTON);
    await fixture.whenStable();

    expect(fixture.componentInstance.form.controls.address.getRawValue()).toEqual({
      street: '83 Rivonia Road',
      suburb: 'Sandown',
      city: 'Sandton',
      province: 'GAUTENG',
      postalCode: '2196',
    });
    expect(el.querySelector<HTMLSelectElement>('#ap-province')!.value).toBe('GAUTENG');
    expect(el.querySelector<HTMLInputElement>('#ap-postal')!.value).toBe('2196');
  });

  it('keeps what was typed for anything Google did not return', async () => {
    await setup(true);
    const address = fixture.componentInstance.form.controls.address;
    address.patchValue({ suburb: 'My own suburb', postalCode: '1234' });

    pick({ ...SANDTON, suburb: '', postalCode: '' });
    await fixture.whenStable();

    expect(address.controls.street.value).toBe('83 Rivonia Road');
    expect(address.controls.city.value).toBe('Sandton');
    expect(address.controls.suburb.value).toBe('My own suburb');
    expect(address.controls.postalCode.value).toBe('1234');
  });

  it('is a plain street field, with no promise of suggestions, when no key is configured', async () => {
    await setup(false);

    expect(el.querySelector('input#ap-street')).not.toBeNull();
    expect(el.textContent).not.toContain('fill in the rest');
  });
});
