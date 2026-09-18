import { Component, DestroyRef, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';

import { Address } from '../../core/address.models';
import { TrimOnBlur } from '../trim-on-blur';
import { AddressSuggestion, GooglePlacesService } from '../../core/google/google-places.service';

export type PickedAddress = Omit<Address, 'province'> & { province: Address['province'] | '' };

/**
 * The street field of an address form, with Google address suggestions when a Maps key is
 * configured. Picking a suggestion emits the whole structured address so the parent can fill
 * suburb, city, province and postal code in one go; with no key, or if Google can't be
 * reached, it is a plain text input and manual entry works exactly as before.
 *
 * <p>Renders the same `admin-input` control as the neighbouring fields (that class is global
 * to the admin portal), so it sits in the form without restyling.
 */
@Component({
  selector: 'app-street-input',
  imports: [ReactiveFormsModule, TrimOnBlur],
  templateUrl: './street-input.html',
  styleUrl: './street-input.scss',
})
export class StreetInput {
  private readonly places = inject(GooglePlacesService);
  private readonly destroyRef = inject(DestroyRef);

  readonly control = input.required<FormControl<string>>();
  readonly inputId = input.required<string>();
  readonly placeholder = input('');
  readonly picked = output<PickedAddress>();

  readonly suggestions = signal<AddressSuggestion[]>([]);
  readonly activeIndex = signal(-1);
  readonly searching = signal(false);

  private readonly typed = new Subject<string>();
  /** Bumped on every request so a slow, superseded response can't overwrite a newer one. */
  private requestId = 0;

  constructor() {
    this.typed
      .pipe(debounceTime(250), distinctUntilChanged(), takeUntilDestroyed(this.destroyRef))
      .subscribe((text) => void this.search(text));
  }

  onInput(): void {
    if (!this.places.enabled) {
      return;
    }
    const text = this.control().value;
    if (text.trim().length < 3) {
      this.close();
    }
    this.typed.next(text);
  }

  onKeydown(event: KeyboardEvent): void {
    const count = this.suggestions().length;
    if (count === 0) {
      return;
    }
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        this.activeIndex.update((i) => (i + 1) % count);
        break;
      case 'ArrowUp':
        event.preventDefault();
        this.activeIndex.update((i) => (i <= 0 ? count - 1 : i - 1));
        break;
      case 'Enter': {
        // Only swallow Enter when it is choosing a suggestion — otherwise it submits the form.
        const active = this.suggestions()[this.activeIndex()];
        if (active) {
          event.preventDefault();
          void this.choose(active);
        }
        break;
      }
      case 'Escape':
        this.close();
        break;
    }
  }

  async choose(suggestion: AddressSuggestion): Promise<void> {
    this.close();
    try {
      this.picked.emit(await suggestion.resolve());
    } catch {
      // Google couldn't resolve it; the person keeps whatever they typed and carries on by hand.
    }
  }

  close(): void {
    this.requestId++;
    this.suggestions.set([]);
    this.activeIndex.set(-1);
    this.searching.set(false);
  }

  private async search(text: string): Promise<void> {
    if (text.trim().length < 3) {
      return;
    }
    const id = ++this.requestId;
    this.searching.set(true);
    try {
      const results = await this.places.suggest(text);
      if (id === this.requestId) {
        this.suggestions.set(results);
        this.activeIndex.set(-1);
      }
    } catch {
      if (id === this.requestId) {
        this.suggestions.set([]);
      }
    } finally {
      if (id === this.requestId) {
        this.searching.set(false);
      }
    }
  }
}
