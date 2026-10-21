import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateDispatchRequest,
  CreateIncidentRequest,
  DispatchAnswer,
  DispatchCreateResponse,
  FreshdeskStatus,
  Incident,
  IncidentKpis,
  IncidentListFilters,
  IncidentLock,
  IncidentProgress,
  PageResponse,
  ResendResult,
  SimulatedTicket,
  TechnicianCandidate,
} from './controller.models';

/** Client for the CONTROLLER-only `/api/v1/controller` endpoints (FR-02–FR-04). */
@Injectable({ providedIn: 'root' })
export class ControllerApiService {
  private readonly http = inject(HttpClient);

  /** Filtering, searching and paging are all delegated to the server — the same reasoning
   * as `AdminUserService.list`. */
  listIncidents(filters: IncidentListFilters): Observable<PageResponse<Incident>> {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);

    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.priority) {
      params = params.set('priority', filters.priority);
    }
    if (filters.unassigned) {
      params = params.set('unassigned', 'true');
    }
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }

    return this.http.get<PageResponse<Incident>>('v1/controller/incidents', { params });
  }

  kpis(): Observable<IncidentKpis> {
    return this.http.get<IncidentKpis>('v1/controller/incidents/kpis');
  }

  getIncident(id: string): Observable<Incident> {
    return this.http.get<Incident>(`v1/controller/incidents/${id}`);
  }

  /** The dispatch that was sent for this incident and how each invited technician has
   * responded so far — the ticket progress view. */
  getIncidentProgress(id: string): Observable<IncidentProgress> {
    return this.http.get<IncidentProgress>(`v1/controller/incidents/${id}/progress`);
  }

  /** Ranked by skill match, availability and proximity — the ranking itself happens
   * server-side (`DispatchMapper`), not in the browser. */
  recommendTechnicians(incidentId: string, skills: string[]): Observable<TechnicianCandidate[]> {
    let params = new HttpParams();
    for (const skill of skills) {
      params = params.append('skills', skill);
    }
    return this.http.get<TechnicianCandidate[]>(
      `v1/controller/incidents/${incidentId}/technicians`,
      { params },
    );
  }

  createDispatch(request: CreateDispatchRequest): Observable<DispatchCreateResponse> {
    return this.http.post<DispatchCreateResponse>('v1/controller/dispatches', request);
  }

  /**
   * Takes (or renews) the hold on dispatching an incident, so two controllers do not both send it
   * out. 409, naming whoever else has it, when someone does.
   */
  lockIncident(id: string): Observable<IncidentLock> {
    return this.http.post<IncidentLock>(`v1/controller/incidents/${id}/lock`, null);
  }

  /** Lets go of the hold; harmless if this controller does not have it. */
  unlockIncident(id: string): Observable<void> {
    return this.http.delete<void>(`v1/controller/incidents/${id}/lock`);
  }

  /** Notifies the technicians who haven't answered again. 409 (with a reason) once it has been
   * answered or expired, or if they were notified moments ago. */
  resendDispatch(dispatchId: string): Observable<ResendResult> {
    return this.http.post<ResendResult>(`v1/controller/dispatches/${dispatchId}/resend`, null);
  }

  /** Logs an incident the Controller took by phone; it joins the queue as NEW. */
  createIncident(request: CreateIncidentRequest): Observable<Incident> {
    return this.http.post<Incident>('v1/controller/incidents', request);
  }

  freshdeskStatus(): Observable<FreshdeskStatus> {
    return this.http.get<FreshdeskStatus>('v1/controller/integrations/freshdesk');
  }

  /** Demo only (404 unless simulation is enabled): raises a ticket in the simulated Freshdesk. */
  simulateFreshdeskTicket(overdue: boolean): Observable<SimulatedTicket> {
    return this.http.post<SimulatedTicket>(
      'v1/controller/simulation/freshdesk-tickets',
      null,
      overdue ? { params: new HttpParams().set('overdue', 'true') } : {},
    );
  }

  /** Demo only: answers a dispatch as one of the technicians it was sent to. */
  simulateTechnicianResponse(
    dispatchId: string,
    technicianId: string,
    response: 'ACCEPTED' | 'DECLINED',
  ): Observable<DispatchAnswer> {
    return this.http.post<DispatchAnswer>(
      `v1/controller/simulation/dispatches/${dispatchId}/respond`,
      { technicianId, response },
    );
  }
}
