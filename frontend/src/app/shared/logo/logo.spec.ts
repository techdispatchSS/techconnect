import { Component, provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { Logo } from './logo';

@Component({
  imports: [Logo],
  template: `
    <app-logo id="a" variant="lockup" />
    <app-logo id="b" variant="mark" [decorative]="true" />
    <app-logo id="c" variant="lockup" label="TechConnect home" />
  `,
})
class Host {}

describe('Logo', () => {
  async function render(): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('draws the wordmark only for the lockup', async () => {
    const el = await render();

    expect(el.querySelector('#a text')?.textContent).toBe('Tech-Connect');
    expect(el.querySelector('#b text')).toBeNull();
    // The mark itself is in both.
    expect(el.querySelectorAll('#a circle').length).toBeGreaterThan(20);
    expect(el.querySelectorAll('#b circle').length).toBeGreaterThan(20);
  });

  it('is announced once, under the product name, unless decorative', async () => {
    const el = await render();

    expect(el.querySelector('#a')?.getAttribute('role')).toBe('img');
    expect(el.querySelector('#a')?.getAttribute('aria-label')).toBe('TechConnect');
    expect(el.querySelector('#c')?.getAttribute('aria-label')).toBe('TechConnect home');
    expect(el.querySelector('#b')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('#b')?.getAttribute('role')).toBeNull();
  });

  it('gives every instance its own mask, so several logos on one page cannot clash', async () => {
    const el = await render();

    const ids = Array.from(el.querySelectorAll('mask')).map((m) => m.getAttribute('id'));
    expect(ids).toHaveLength(3);
    expect(new Set(ids).size).toBe(3);
    // And each artwork points at its own.
    const refs = Array.from(el.querySelectorAll('g[mask]')).map((g) => g.getAttribute('mask'));
    expect(refs).toEqual(ids.map((id) => `url(#${id})`));
  });
});
