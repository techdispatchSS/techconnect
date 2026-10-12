import { NgTemplateOutlet } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

type Circle = readonly [cx: number, cy: number, r: number];
type Ring = readonly [cx: number, cy: number, r: number, strokeWidth: number];
type Link = readonly [x1: number, y1: number, x2: number, y2: number];

/** The network nodes' knock-outs in the orange ring, so the links never touch the ring. */
const MASK_HOLES: readonly Circle[] = [
  [48, 196, 6],
  [33, 245, 9],
  [80, 294, 9],
  [108, 254, 6],
  [141, 312, 6],
  [28, 332, 7.5],
  [70, 360, 7.5],
  [119, 357, 9],
  [166, 390, 7.5],
  [70, 425, 6],
  [117, 425, 9],
  [185, 448, 9],
  [131, 485, 9],
];

const NODE_RINGS: readonly Ring[] = [
  [48, 196, 8.5, 5],
  [33, 245, 14, 10],
  [80, 294, 14, 10],
  [108, 254, 8.5, 5],
  [141, 312, 8.5, 5],
  [28, 332, 11, 7],
  [70, 360, 11, 7],
  [119, 357, 14, 10],
  [166, 390, 11, 7],
  [70, 425, 8.5, 5],
  [117, 425, 14, 10],
  [185, 448, 14, 10],
  [131, 485, 14, 10],
];

const LINKS: readonly Link[] = [
  [48, 196, 33, 245],
  [33, 245, 80, 294],
  [80, 294, 108, 254],
  [80, 294, 119, 357],
  [141, 312, 119, 357],
  [28, 332, 70, 360],
  [70, 360, 119, 357],
  [119, 357, 117, 425],
  [117, 425, 70, 425],
  [117, 425, 185, 448],
  [166, 390, 185, 448],
  [185, 448, 131, 485],
];

const TRACES: readonly string[] = ['291,402 215,402 166,390', '225,225 140,225 108,254'];

let nextInstance = 0;

/**
 * The TechConnect mark, or the mark with its wordmark ("lockup").
 *
 * <p>Drawn inline rather than loaded as an image so that (a) the wordmark picks up the page's
 * Barlow font — an SVG used as an `<img>` cannot see page fonts — and (b) the grey parts can
 * follow the surface they sit on. The orange is the brand colour and never changes; the grey
 * reads `--tc-logo-ink` (set in `styles.scss` per theme, and overridden on the always-dark
 * sidebars and the login hero).
 *
 * <p>Sized by `height`; the width follows the artwork's aspect ratio. Pass `decorative` when
 * the product name is already beside it in text, so a screen reader doesn't say it twice.
 */
@Component({
  selector: 'app-logo',
  imports: [NgTemplateOutlet],
  templateUrl: './logo.html',
  styleUrl: './logo.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[style.height]': 'height()',
    '[attr.role]': 'decorative() ? null : "img"',
    '[attr.aria-label]': 'decorative() ? null : label()',
    '[attr.aria-hidden]': 'decorative() ? "true" : null',
  },
})
export class Logo {
  readonly variant = input<'mark' | 'lockup'>('lockup');
  readonly height = input('2rem');
  readonly label = input('TechConnect');
  readonly decorative = input(false);

  /** Unique per instance: several logos on one page must not share a mask id. */
  protected readonly maskId = `tc-logo-mask-${nextInstance++}`;
  protected readonly maskRef = `url(#${this.maskId})`;

  protected readonly maskHoles = MASK_HOLES;
  protected readonly nodeRings = NODE_RINGS;
  protected readonly links = LINKS;
  protected readonly traces = TRACES;
}
