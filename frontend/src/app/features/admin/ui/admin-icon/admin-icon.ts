import { Component, input } from '@angular/core';

export type AdminIconName =
  | 'people'
  | 'invite'
  | 'add'
  | 'audit'
  | 'search'
  | 'chevron-right'
  | 'arrow-left'
  | 'copy'
  | 'lock'
  | 'mail'
  | 'check-circle'
  | 'log-out'
  | 'badge'
  | 'key'
  | 'menu'
  | 'x';

/**
 * Thin-stroke icon set for the admin portal's "Industry" design system (Lucide-style,
 * stroke-width 1.5). Kept as inline SVG rather than a font so the admin bundle does not
 * pull in a whole icon font just for a dozen glyphs.
 */
@Component({
  selector: 'app-admin-icon',
  host: {
    '[style.width.px]': 'size()',
    '[style.height.px]': 'size()',
  },
  template: `
    <svg
      viewBox="0 0 24 24"
      width="100%"
      height="100%"
      fill="none"
      stroke="currentColor"
      stroke-width="1.5"
      stroke-linecap="round"
      stroke-linejoin="round"
    >
      @switch (name()) {
        @case ('people') {
          <circle cx="9" cy="8" r="3.2" />
          <path d="M3 20c0-3.2 2.7-5.2 6-5.2s6 2 6 5.2" />
          <path d="M16 5.5a3 3 0 0 1 0 5.6M18 20c0-2.4-1-4-2.5-4.8" />
        }
        @case ('invite') {
          <rect x="3" y="5.5" width="18" height="13" />
          <path d="m3 7 9 6 9-6" />
        }
        @case ('add') {
          <circle cx="10" cy="8" r="3.2" />
          <path d="M3.5 20c0-3.2 2.9-5.2 6.5-5.2" />
          <path d="M17 13v6M14 16h6" />
        }
        @case ('audit') {
          <path d="M4 4h13l3 3v13H4z" />
          <path d="M8 10h8M8 14h8M8 18h5" />
        }
        @case ('search') {
          <circle cx="11" cy="11" r="7" />
          <path d="m20 20-3.5-3.5" />
        }
        @case ('chevron-right') {
          <path d="m9 6 6 6-6 6" />
        }
        @case ('arrow-left') {
          <path d="M19 12H5M11 6l-6 6 6 6" />
        }
        @case ('copy') {
          <rect x="9" y="9" width="12" height="12" rx="1" />
          <path d="M5 15V4a1 1 0 0 1 1-1h11" />
        }
        @case ('lock') {
          <rect x="4.5" y="10.5" width="15" height="9.5" />
          <path d="M8 10.5V7a4 4 0 0 1 8 0v3.5" />
        }
        @case ('mail') {
          <rect x="3" y="5.5" width="18" height="13" />
          <path d="m4 6.5 8 6 8-6" />
        }
        @case ('check-circle') {
          <circle cx="12" cy="12" r="8.5" />
          <path d="m8.2 12.3 2.6 2.6 5-5.4" />
        }
        @case ('log-out') {
          <path d="M9 20H5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h4" />
          <path d="M16 16l4-4-4-4M20 12H9" />
        }
        @case ('badge') {
          <rect x="5" y="4" width="14" height="17" />
          <circle cx="12" cy="10" r="2.6" />
          <path d="M8 17.5c.6-1.9 2-2.9 4-2.9s3.4 1 4 2.9" />
        }
        @case ('key') {
          <circle cx="8" cy="15" r="3.8" />
          <path d="m10.6 12.4 8.4-8.4M15 6l2.5 2.5M18 3l3 3" />
        }
        @case ('menu') {
          <path d="M4 6h16M4 12h16M4 18h16" />
        }
        @case ('x') {
          <path d="M6 6l12 12M18 6 6 18" />
        }
      }
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
  `,
})
export class AdminIcon {
  readonly name = input.required<AdminIconName>();
  readonly size = input(16);
}
