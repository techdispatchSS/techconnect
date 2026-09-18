import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * A shimmering placeholder shown where backend data hasn't arrived yet, so a figure or row
 * doesn't briefly read as a real "0" or "No results" before the real value lands.
 *
 * <p>Sized by the caller, and coloured from the surrounding text colour, so it works on the
 * dark admin/controller surfaces and on the light ones without any per-theme tokens. Decorative
 * only: the region that contains it should carry `aria-busy` while loading.
 */
@Component({
  selector: 'app-skeleton',
  template: '',
  styleUrl: './skeleton.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    'aria-hidden': 'true',
    '[style.width]': 'width()',
    '[style.height]': 'height()',
    '[style.border-radius]': 'radius()',
  },
})
export class Skeleton {
  readonly width = input('100%');
  readonly height = input('1em');
  readonly radius = input('3px');
}
