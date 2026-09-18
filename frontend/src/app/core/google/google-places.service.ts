import { Injectable } from '@angular/core';

import { environment } from '../../../environments/environment';
import { PlaceAddressComponent, addressFromComponents } from './place-address';

export interface AddressSuggestion {
  readonly mainText: string;
  readonly secondaryText: string;
  /** Resolves the suggestion into structured address fields. */
  resolve(): Promise<ReturnType<typeof addressFromComponents>>;
}

// Only the slice of the Places (New) JS API used here — typed locally rather than pulling in
// the full @types/google.maps package for four calls.
interface PlacePrediction {
  readonly mainText?: { readonly text: string };
  readonly secondaryText?: { readonly text: string };
  readonly text: { readonly text: string };
  toPlace(): {
    fetchFields(request: { fields: string[] }): Promise<unknown>;
    readonly addressComponents?: readonly PlaceAddressComponent[];
  };
}

interface PlacesLibrary {
  AutocompleteSessionToken: new () => object;
  AutocompleteSuggestion: {
    fetchAutocompleteSuggestions(request: {
      input: string;
      sessionToken: object;
      includedRegionCodes: string[];
    }): Promise<{ suggestions: { placePrediction: PlacePrediction | null }[] }>;
  };
}

interface GoogleNamespace {
  maps: { importLibrary(name: 'places'): Promise<PlacesLibrary> };
}

const MIN_INPUT_LENGTH = 3;

/**
 * Address search backed by Google Places (New). Loads Google's script lazily on first use, so
 * screens that never search an address — and deployments with no key configured — pay nothing.
 *
 * <p>Suggestions are limited to South Africa, matching the province list the rest of the address
 * form is built around. Every lookup within one typing session shares a session token, which is
 * how Google bills a search-then-select as a single request.
 */
@Injectable({ providedIn: 'root' })
export class GooglePlacesService {
  readonly enabled = environment.googleMapsApiKey.trim().length > 0;

  private library: Promise<PlacesLibrary> | null = null;
  private sessionToken: object | null = null;

  async suggest(input: string): Promise<AddressSuggestion[]> {
    const query = input.trim();
    if (!this.enabled || query.length < MIN_INPUT_LENGTH) {
      return [];
    }

    const places = await this.load();
    this.sessionToken ??= new places.AutocompleteSessionToken();

    const { suggestions } = await places.AutocompleteSuggestion.fetchAutocompleteSuggestions({
      input: query,
      sessionToken: this.sessionToken,
      includedRegionCodes: ['za'],
    });

    return suggestions
      .map((s) => s.placePrediction)
      .filter((p): p is PlacePrediction => p !== null)
      .map((prediction) => ({
        mainText: prediction.mainText?.text ?? prediction.text.text,
        secondaryText: prediction.secondaryText?.text ?? '',
        resolve: async () => {
          const place = prediction.toPlace();
          await place.fetchFields({ fields: ['addressComponents'] });
          // A selection ends the billing session; the next search starts a fresh one.
          this.sessionToken = null;
          return addressFromComponents(place.addressComponents ?? []);
        },
      }));
  }

  private load(): Promise<PlacesLibrary> {
    this.library ??= this.injectScript().then(() => {
      const google = (window as unknown as { google: GoogleNamespace }).google;
      return google.maps.importLibrary('places');
    });
    // A failed load (offline, blocked key) shouldn't stay cached forever.
    this.library.catch(() => (this.library = null));
    return this.library;
  }

  private injectScript(): Promise<void> {
    const existing = (window as unknown as { google?: GoogleNamespace }).google?.maps;
    if (existing) {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src =
        'https://maps.googleapis.com/maps/api/js' +
        `?key=${encodeURIComponent(environment.googleMapsApiKey)}&v=weekly&loading=async`;
      script.async = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('Could not load Google Maps.'));
      document.head.appendChild(script);
    });
  }
}
