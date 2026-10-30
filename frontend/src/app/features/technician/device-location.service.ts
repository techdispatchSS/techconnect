import { Injectable } from '@angular/core';

import { Coordinates } from './technician.models';

/**
 * The phone's position, from the browser. A thin wrapper so the screens can be tested without
 * a real `navigator.geolocation`. Rejects when the device has none or the person said no.
 */
@Injectable({ providedIn: 'root' })
export class DeviceLocationService {
  current(): Promise<Coordinates> {
    return new Promise((resolve, reject) => {
      if (typeof navigator === 'undefined' || !navigator.geolocation) {
        reject(new Error('Location is not available on this device.'));
        return;
      }
      navigator.geolocation.getCurrentPosition(
        (position) =>
          resolve({ latitude: position.coords.latitude, longitude: position.coords.longitude }),
        (error) => reject(error),
        // A street-level fix is plenty for "how far away are they", and saves the battery.
        { enableHighAccuracy: false, timeout: 15_000, maximumAge: 5 * 60_000 },
      );
    });
  }
}
