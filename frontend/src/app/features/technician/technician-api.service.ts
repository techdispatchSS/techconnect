import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Coordinates, DispatchAnswer, DispatchOffer, MyAvailability } from './technician.models';

/**
 * Client for the TECHNICIAN-only `/api/v1/technician` endpoints. Every call is about the
 * signed-in technician; the server takes who that is from the token, never from the request.
 */
@Injectable({ providedIn: 'root' })
export class TechnicianApiService {
  private readonly http = inject(HttpClient);

  availability(): Observable<MyAvailability> {
    return this.http.get<MyAvailability>('/v1/technician/availability');
  }

  /** On or off duty (T-14). 409 while they hold a job that isn't complete yet. */
  setAvailable(available: boolean): Observable<MyAvailability> {
    return this.http.put<MyAvailability>('/v1/technician/availability', { available });
  }

  /** FR-05: sent when the app opens or comes back to the foreground. */
  reportLocation(coordinates: Coordinates): Observable<MyAvailability> {
    return this.http.put<MyAvailability>('/v1/technician/location', coordinates);
  }

  /** Offers still inside their window that I haven't answered, soonest to lapse first. */
  offers(): Observable<DispatchOffer[]> {
    return this.http.get<DispatchOffer[]>('/v1/technician/dispatches');
  }

  /** 409 with a reason when it was taken, has lapsed, or was already answered. */
  accept(dispatchId: string): Observable<DispatchAnswer> {
    return this.http.post<DispatchAnswer>(`/v1/technician/dispatches/${dispatchId}/accept`, null);
  }

  decline(dispatchId: string): Observable<DispatchAnswer> {
    return this.http.post<DispatchAnswer>(`/v1/technician/dispatches/${dispatchId}/decline`, null);
  }
}
