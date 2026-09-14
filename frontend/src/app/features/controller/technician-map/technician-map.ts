import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import * as L from 'leaflet';

import { TechnicianCandidate } from '../controller.models';

const ORANGE = '#f08221';

/** One reported technician position, or the incident's own site — both plotted the same way. */
export interface MapPin {
  readonly id: string;
  readonly label: string;
  readonly latitude: number;
  readonly longitude: number;
  readonly kind: 'site' | 'available' | 'busy';
  readonly popup: string;
}

/**
 * Live technician coverage relative to the incident site (FR-04). Ported from the design
 * mockup's `techconnect-map.js` custom element into a proper Angular component backed by a
 * real `leaflet` dependency, rather than loading it from a CDN at runtime.
 */
@Component({
  selector: 'app-technician-map',
  template: `<div #host class="tm-host"></div>`,
  styleUrl: './technician-map.scss',
})
export class TechnicianMap implements AfterViewInit, OnChanges, OnDestroy {
  @Input() siteLatitude: number | null = null;
  @Input() siteLongitude: number | null = null;
  @Input() siteLabel = 'Incident site';
  @Input() technicians: readonly TechnicianCandidate[] = [];

  @ViewChild('host', { static: true }) private readonly host!: ElementRef<HTMLDivElement>;

  private map: L.Map | null = null;
  private viewReady = false;

  ngAfterViewInit(): void {
    this.viewReady = true;
    this.render();
  }

  ngOnChanges(): void {
    if (this.viewReady) {
      this.render();
    }
  }

  ngOnDestroy(): void {
    this.map?.remove();
    this.map = null;
  }

  private render(): void {
    const pins = this.buildPins();

    if (!this.map) {
      this.map = L.map(this.host.nativeElement, {
        zoomControl: true,
        attributionControl: true,
        scrollWheelZoom: false,
      });
      L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 19,
        attribution: '&copy; OpenStreetMap contributors',
      }).addTo(this.map);
      // Dark-mode the light OSM raster to match the controller dashboard's theme.
      const pane = this.map.getPane('tilePane');
      if (pane) {
        pane.style.filter = 'invert(1) hue-rotate(185deg) brightness(0.92) contrast(0.88) saturate(0.55)';
      }
    } else {
      this.map.eachLayer((layer) => {
        if (layer instanceof L.Marker || layer instanceof L.Polyline) {
          this.map?.removeLayer(layer);
        }
      });
    }

    if (pins.length === 0) {
      this.map.setView([-26.07, 28.03], 10);
      return;
    }

    const site = pins.find((p) => p.kind === 'site');
    const bounds: L.LatLngExpression[] = [];

    for (const pin of pins) {
      const marker = L.marker([pin.latitude, pin.longitude], { icon: this.pinIcon(pin) }).addTo(this.map);
      marker.bindPopup(pin.popup);
      bounds.push([pin.latitude, pin.longitude]);

      if (site && pin.kind !== 'site') {
        L.polyline(
          [
            [site.latitude, site.longitude],
            [pin.latitude, pin.longitude],
          ],
          { color: ORANGE, weight: 1, opacity: pin.kind === 'busy' ? 0.18 : 0.4, dashArray: '4 5' },
        ).addTo(this.map);
      }
    }

    this.map.fitBounds(L.latLngBounds(bounds).pad(0.18));
    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  private buildPins(): MapPin[] {
    const pins: MapPin[] = [];

    if (this.siteLatitude != null && this.siteLongitude != null) {
      pins.push({
        id: 'site',
        label: '★',
        latitude: this.siteLatitude,
        longitude: this.siteLongitude,
        kind: 'site',
        popup: `<strong>${this.siteLabel}</strong><br>Incident site`,
      });
    }

    for (const tech of this.technicians) {
      if (tech.latitude == null || tech.longitude == null) {
        continue;
      }
      pins.push({
        id: tech.id,
        label: initials(tech.name),
        latitude: tech.latitude,
        longitude: tech.longitude,
        kind: tech.status === 'AVAILABLE' ? 'available' : 'busy',
        popup: `<strong>${tech.name}</strong><br>${tech.status === 'AVAILABLE' ? 'Available' : tech.status === 'ON_JOB' ? 'On a job' : 'Offline'}${tech.distanceKm != null ? ' · ' + tech.distanceKm + ' km away' : ''}`,
      });
    }

    return pins;
  }

  private pinIcon(pin: MapPin): L.DivIcon {
    const site = pin.kind === 'site';
    const busy = pin.kind === 'busy';
    const size = site ? 30 : 24;
    const bg = site ? ORANGE : busy ? '#0e1013' : 'rgba(240,130,33,0.18)';
    const border = site ? ORANGE : busy ? '#8a5a2b' : ORANGE;
    const color = site ? '#12140f' : busy ? '#c9905c' : '#f0a265';

    return L.divIcon({
      className: '',
      iconSize: [size, size],
      iconAnchor: [size / 2, size / 2],
      html:
        `<span style="display:grid;place-items:center;width:${size}px;height:${size}px;` +
        `border-radius:50%;background:${bg};border:2px solid ${border};color:${color};` +
        `font-family:Barlow,system-ui,sans-serif;font-weight:700;font-size:${site ? 13 : 11}px;` +
        `box-shadow:0 0 0 4px rgba(240,130,33,0.12);">${pin.label}</span>`,
    });
  }
}

function initials(name: string): string {
  return name
    .split(' ')
    .map((w) => w[0])
    .join('');
}
