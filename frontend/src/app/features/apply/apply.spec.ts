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
    // Nobody is assumed to be South African: the country is chosen, and the form follows it.
    fixture.componentInstance.form.controls.address.controls.country.setValue('ZA');
    fixture.detectChanges();
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

  async function chooseIn(input: HTMLInputElement, files: File[]): Promise<void> {
    Object.defineProperty(input, 'files', { value: files, configurable: true });
    input.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  /** The upload area for a required document, by position: ID, diploma, proof of residence. */
  function requiredInput(index: number): HTMLInputElement {
    return el
      .querySelectorAll<HTMLInputElement>('.ap-req')
      [index].querySelector('input[type="file"]')!;
  }

  async function attachRequired(): Promise<void> {
    for (const name of ['id.pdf', 'matric.pdf', 'residence.pdf']) {
      // Each upload area is replaced by the file's row once filled, so the next one is always
      // the first area still empty.
      const empty = el.querySelector<HTMLInputElement>('.ap-req .ap-drop input[type="file"]')!;
      await chooseIn(empty, [pdf(name)]);
    }
  }

  async function attach(...files: File[]): Promise<void> {
    await chooseIn(el.querySelector<HTMLInputElement>('.ap-drop--cert input[type="file"]')!, files);
  }

  async function send(): Promise<void> {
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('sends nothing for an empty form and says which documents are required', async () => {
    await setup();
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Upload your identification document');
    expect(el.textContent).toContain('Upload your high school diploma');
    expect(el.textContent).toContain('Upload your proof of residence');
    expect(el.textContent).toContain('First name is required');
  });

  it('will not send without the three required documents, even if every field is valid', async () => {
    await setup();
    fillValidForm();
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Upload your identification document');
  });

  it('names the one document still missing', async () => {
    await setup();
    fillValidForm();
    await chooseIn(requiredInput(0), [pdf('id.pdf')]);
    await chooseIn(requiredInput(1), [pdf('matric.pdf')]);
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Upload your proof of residence');
    expect(el.textContent).not.toContain('Upload your identification document');
  });

  it('does not let a certification stand in for a required document', async () => {
    await setup();
    fillValidForm();
    await attach(pdf('ccna.pdf'));
    type('input[id^="ap-doc-title-"]', 'CCNA');
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Upload your identification document');
  });

  it('will not send an ID number with a bad checksum, and says why', async () => {
    await setup();
    fillValidForm();
    type('#ap-id', '9001015000084');
    await attachRequired();
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('Enter a valid 13-digit South African ID number');
  });

  it('requires an ID number', async () => {
    await setup();
    fillValidForm();
    type('#ap-id', '');
    await attachRequired();
    await send();

    expect(submit).not.toHaveBeenCalled();
    expect(el.textContent).toContain('ID number is required');
  });

  it('accepts an ID number typed with spaces and sends it as plain digits', async () => {
    await setup();
    fillValidForm();
    type('#ap-id', '900101 5000 085');
    await attachRequired();
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
    const [request] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
    expect(request.idNumber).toBe('9001015000085');
  });

  it('will not send without agreeing to the privacy notice', async () => {
    await setup();
    fillValidForm();
    (el.querySelector('input[type="checkbox"]') as HTMLInputElement).click(); // untick
    await attachRequired();
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

  it('refuses a disallowed file in a required slot and leaves the slot empty', async () => {
    await setup();

    await chooseIn(requiredInput(0), [new File(['MZ'], 'virus.exe')]);

    expect(el.textContent).toContain("isn't a PDF, JPG or PNG");
    expect(el.querySelectorAll('.ap-req--done')).toHaveLength(0);
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
    await attachRequired();
    await attach(pdf('ccna.pdf'));
    type('input[id^="ap-doc-title-"]', '  CCNA Routing  ');
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
    const [request, files] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
    expect(request.firstName).toBe('Nomsa');
    expect(request.address).toEqual({
      country: 'ZA',
      street: '1 Test Street',
      suburb: 'Umhlanga',
      city: 'Durban',
      province: 'KWAZULU_NATAL',
      postalCode: '4001',
    });
    expect(request.documents).toEqual([
      { kind: 'ID_DOCUMENT', title: 'Identification document' },
      { kind: 'MATRIC_CERTIFICATE', title: 'High school diploma' },
      { kind: 'PROOF_OF_RESIDENCE', title: 'Proof of residence' },
      { kind: 'CERTIFICATE', title: 'CCNA Routing' },
    ]);
    expect(request.consent).toBe(true);
    expect(request.website).toBe('');
    expect(files.map((f) => f.name)).toEqual(['id.pdf', 'matric.pdf', 'residence.pdf', 'ccna.pdf']);
  });

  it('invites a click to upload each required document, then shows the file chosen', async () => {
    await setup();

    const tiles = [...el.querySelectorAll('.ap-req .ap-drop-title')].map((t) =>
      t.textContent!.trim(),
    );
    expect(tiles).toEqual([
      'Click here to upload your identification document',
      'Click here to upload your high school diploma',
      'Click here to upload your proof of residence',
    ]);

    await chooseIn(requiredInput(0), [pdf('my-id.pdf')]);

    const first = el.querySelectorAll('.ap-req')[0];
    expect(first.classList.contains('ap-req--done')).toBe(true);
    expect(first.textContent).toContain('my-id.pdf');
    expect(first.querySelector('.ap-drop')).toBeNull();
  });

  it('takes more than one file for a required document, like both sides of an ID', async () => {
    await setup();
    await chooseIn(requiredInput(0), [pdf('front.pdf')]);

    const first = el.querySelectorAll('.ap-req')[0];
    expect(first.textContent).toContain('+ Add another file');

    await chooseIn(requiredInput(0), [pdf('back.pdf')]);

    expect(first.textContent).toContain('front.pdf');
    expect(first.textContent).toContain('back.pdf');
    expect(first.querySelectorAll('.ap-req-file')).toHaveLength(2);
    expect(el.textContent).toContain('2 of 10 files added');
  });

  it('takes several files picked at once for one required document', async () => {
    await setup();
    await chooseIn(requiredInput(2), [pdf('bill-1.pdf'), pdf('bill-2.pdf'), pdf('bill-3.pdf')]);

    expect(el.querySelectorAll('.ap-req')[2].querySelectorAll('.ap-req-file')).toHaveLength(3);
  });

  it('stops offering more once a required document has all it may take', async () => {
    await setup();
    await chooseIn(requiredInput(0), [pdf('1.pdf'), pdf('2.pdf'), pdf('3.pdf'), pdf('4.pdf')]);
    const first = el.querySelectorAll('.ap-req')[0];
    expect(first.textContent).not.toContain('+ Add another file');

    await chooseIn(
      el.querySelectorAll<HTMLInputElement>('.ap-req .ap-drop input[type="file"]')[0],
      [pdf('m1.pdf'), pdf('m2.pdf'), pdf('m3.pdf'), pdf('m4.pdf'), pdf('m5.pdf')],
    );
    expect(el.textContent).toContain('You can add up to 4 files');
  });

  it('removes one of several files without losing the rest', async () => {
    await setup();
    await chooseIn(requiredInput(0), [pdf('front.pdf'), pdf('back.pdf')]);

    el.querySelectorAll<HTMLButtonElement>('.ap-req')[0]
      .querySelector<HTMLButtonElement>('.ap-file-remove')!
      .click();
    await fixture.whenStable();

    const first = el.querySelectorAll('.ap-req')[0];
    expect(first.textContent).not.toContain('front.pdf');
    expect(first.textContent).toContain('back.pdf');
  });

  it('puts a certification on the page unnamed, prompts for its name and focuses it', async () => {
    await setup();
    document.body.appendChild(el);

    expect(el.querySelector('.ap-drop--cert .ap-drop-title')!.textContent).toContain(
      'Click here to upload a certification',
    );

    await attach(pdf('scan0042.pdf'));

    const name = el.querySelector<HTMLInputElement>('input[id^="ap-doc-title-"]')!;
    expect(name.value).toBe('');
    expect(el.textContent).toContain('What is this certification called?');
    expect(el.textContent).toContain('Give this certification a name before you submit.');
    expect(document.activeElement).toBe(name);
    el.remove();
  });

  it('will not send while an uploaded certification has no name', async () => {
    await setup();
    fillValidForm();
    await attachRequired();
    await attach(pdf('scan0042.pdf'));

    await send();

    expect(submit).not.toHaveBeenCalled();

    type('input[id^="ap-doc-title-"]', 'Hikvision installer');
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
  });

  it('sends with no certifications at all: they are optional', async () => {
    await setup();
    fillValidForm();
    await attachRequired();
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
    const [request] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
    expect(request.documents.map((d) => d.kind)).toEqual([
      'ID_DOCUMENT',
      'MATRIC_CERTIFICATE',
      'PROOF_OF_RESIDENCE',
    ]);
  });

  it('shows the confirmation instead of the form once accepted', async () => {
    await setup();
    fillValidForm();
    await attachRequired();
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
    await attachRequired();
    await send();

    expect(el.querySelector('[role="alert"].auth-error')!.textContent).toContain(
      'contains scripts',
    );
    expect(el.querySelector('form')).not.toBeNull();
  });

  it('explains a rate limit rather than failing silently', async () => {
    await setup(() => throwError(() => new HttpErrorResponse({ status: 429, error: {} })));
    fillValidForm();
    await attachRequired();
    await send();

    expect(el.textContent).toContain('Too many applications');
  });

  it('does not send twice while a submission is in flight', async () => {
    const { Subject } = await import('rxjs');
    const pending = new Subject<ApplicationReceivedResponse>();
    await setup(() => pending);
    fillValidForm();
    await attachRequired();

    await send();
    await send();

    expect(submit).toHaveBeenCalledTimes(1);
  });

  describe('from another country', () => {
    async function inKenya(): Promise<void> {
      await setup();
      type('#ap-first', 'Wanjiru');
      type('#ap-last', 'Kamau');
      type('#ap-email', 'wanjiru@example.com');
      type('#ap-phone', '+254 712 345 678');
      choose('#ap-country', 'KE');
      await fixture.whenStable();
      type('#ap-id', 'A1234567');
      type('#ap-street', '12 Moi Avenue');
      type('#ap-city', 'Nairobi');
      type('#ap-region', 'Nairobi County');
      type('#ap-experience', 'Eight years installing network and CCTV systems for businesses.');
      (el.querySelector('input[type="checkbox"]') as HTMLInputElement).click();
      await attachRequired();
    }

    it('takes a typed region and no suburb or postcode, and a passport-style ID', async () => {
      await inKenya();
      await send();

      expect(submit).toHaveBeenCalledTimes(1);
      const [request] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
      expect(request.idNumber).toBe('A1234567');
      expect(request.address).toEqual({
        country: 'KE',
        street: '12 Moi Avenue',
        suburb: '',
        city: 'Nairobi',
        province: 'Nairobi County',
        postalCode: '',
      });
    });

    it('assumes no country: the country comes first and has to be chosen', async () => {
      await setup();

      const country = el.querySelector<HTMLSelectElement>('#ap-country')!;
      expect(country.value).toBe('');
      expect(el.querySelector('select#ap-province')).toBeNull();
      expect(el.textContent).toContain('Identification number');
      expect(el.textContent).not.toContain('South African ID number');

      await send();
      expect(el.textContent).toContain('Choose the country you are based in');
    });

    it('offers every country, not only Africa', async () => {
      await setup();
      const names = Array.from(el.querySelectorAll<HTMLOptionElement>('#ap-country option')).map(
        (o) => o.textContent!.trim(),
      );
      for (const wanted of ['South Africa', 'Kenya', 'United Kingdom', 'United States', 'India']) {
        expect(names).toContain(wanted);
      }
      expect(names.length).toBeGreaterThan(200);
    });

    it('swaps the province list for a text box once the country is not South Africa', async () => {
      await setup();
      choose('#ap-country', 'ZA');
      await fixture.whenStable();
      expect(el.querySelector('select#ap-province')).not.toBeNull();
      expect(el.querySelector('#ap-region')).toBeNull();
      expect(el.textContent).toContain('South African ID number');

      choose('#ap-country', 'NG');
      await fixture.whenStable();

      expect(el.querySelector('select#ap-province')).toBeNull();
      expect(el.querySelector('#ap-region')).not.toBeNull();
      expect(el.textContent).toContain('Identification number');
      expect(el.textContent).toContain('Postal code (optional)');
    });

    it('asks for the phone number with the chosen country’s calling code', async () => {
      await setup();
      choose('#ap-country', 'GB');
      await fixture.whenStable();

      expect(el.querySelector<HTMLInputElement>('#ap-phone')!.placeholder).toContain('+44');
      expect(el.textContent).toContain('Start with +44');
    });

    it('accepts identification numbers with slashes, dots, hyphens and spaces', async () => {
      for (const id of ['63-123456 A 12', 'AB/1234/56', 'A.123.456-7', 'GHA-123456789-0']) {
        TestBed.resetTestingModule();
        await inKenya();
        type('#ap-id', id);
        await send();

        expect(submit, id).toHaveBeenCalledTimes(1);
        const [request] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
        expect(request.idNumber).toBe(id.replace(/\s+/g, ''));
      }
    });

    it('still refuses an identification number that is markup', async () => {
      await inKenya();
      type('#ap-id', '<script>alert(1)');
      await send();

      expect(submit).not.toHaveBeenCalled();
      expect(el.textContent).toContain('Enter your ID or passport number');
    });

    it('accepts a postcode that is not four digits', async () => {
      await inKenya();
      type('#ap-postal', '00100');
      await send();

      expect(submit).toHaveBeenCalledTimes(1);
      const [request] = submit.mock.calls[0] as [SubmitApplicationRequest, File[]];
      expect(request.address.postalCode).toBe('00100');
    });

    it('still refuses a postcode that does not look like one', async () => {
      await inKenya();
      type('#ap-postal', '<script>');
      await send();

      expect(submit).not.toHaveBeenCalled();
      expect(el.textContent).toContain('Enter a valid postal code');
    });

    it('forgets a South African province when the country changes', async () => {
      await setup();
      choose('#ap-country', 'ZA');
      await fixture.whenStable();
      choose('#ap-province', 'GAUTENG');
      choose('#ap-country', 'KE');
      await fixture.whenStable();

      expect(fixture.componentInstance.form.controls.address.controls.province.value).toBe('');
    });

    it('holds a South African to the South African ID again if they switch back', async () => {
      await inKenya();
      choose('#ap-country', 'ZA');
      await fixture.whenStable();
      type('#ap-id', 'A1234567');
      expect(fixture.componentInstance.form.controls.idNumber.hasError('saId')).toBe(true);
    });
  });

  it('keeps the honeypot out of sight of people and assistive tech', async () => {
    await setup();
    const honeypot = el.querySelector('.ap-hp')!;
    expect(honeypot.getAttribute('aria-hidden')).toBe('true');
    expect(honeypot.querySelector('input')!.getAttribute('tabindex')).toBe('-1');
  });
});

const SANDTON: PickedPlace = {
  country: 'ZA',
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
      country: 'ZA',
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

describe('Apply: skills', () => {
  let fixture: ComponentFixture<Apply>;
  let el: HTMLElement;

  async function setup(): Promise<void> {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        { provide: ApplyApiService, useValue: { submit: vi.fn() } },
      ],
    });
    fixture = TestBed.createComponent(Apply);
    el = fixture.nativeElement;
    await fixture.whenStable();
  }

  async function typeSkill(text: string): Promise<void> {
    const box = el.querySelector<HTMLInputElement>('#ap-skill-draft')!;
    box.value = text;
    box.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function press(key: string): Promise<KeyboardEvent> {
    const box = el.querySelector<HTMLInputElement>('#ap-skill-draft')!;
    const event = new KeyboardEvent('keydown', { key, cancelable: true, bubbles: true });
    box.dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  const rows = (): string[] =>
    [...el.querySelectorAll('.ap-skill-name')].map((e) => e.textContent!.trim());

  it('puts each skill on its own row when it is added', async () => {
    await setup();

    await typeSkill('CCTV installation');
    el.querySelector<HTMLButtonElement>('.ap-skill-add-btn')!.click();
    await fixture.whenStable();
    await typeSkill('Fibre splicing');
    el.querySelector<HTMLButtonElement>('.ap-skill-add-btn')!.click();
    await fixture.whenStable();

    expect(rows()).toEqual(['CCTV installation', 'Fibre splicing']);
    expect(el.querySelector<HTMLInputElement>('#ap-skill-draft')!.value).toBe('');
  });

  it('adds on Enter without submitting the application', async () => {
    await setup();
    await typeSkill('Structured cabling');

    const event = await press('Enter');

    expect(event.defaultPrevented).toBe(true);
    expect(rows()).toEqual(['Structured cabling']);
  });

  it('splits a pasted comma-separated list into rows and ignores repeats', async () => {
    await setup();

    await typeSkill('CCTV, fibre, cabling, cctv');
    await press('Enter');

    expect(rows()).toEqual(['CCTV', 'fibre', 'cabling']);
  });

  it('removes a skill', async () => {
    await setup();
    await typeSkill('CCTV, fibre');
    await press('Enter');

    el.querySelector<HTMLButtonElement>('.ap-skill .ap-file-remove')!.click();
    await fixture.whenStable();

    expect(rows()).toEqual(['fibre']);
  });

  it('stops at the limit', async () => {
    await setup();
    await typeSkill(Array.from({ length: 25 }, (_, i) => `Skill ${i}`).join(', '));
    await press('Enter');

    expect(rows()).toHaveLength(20);
    expect(el.textContent).toContain('That is the most we can take');
  });

  it('sends one skill per line, including one still in the box', async () => {
    await setup();
    await typeSkill('CCTV');
    await press('Enter');
    await typeSkill('Fibre splicing');

    fixture.componentInstance.addSkill();

    expect(fixture.componentInstance.skills()).toEqual(['CCTV', 'Fibre splicing']);
  });
});
