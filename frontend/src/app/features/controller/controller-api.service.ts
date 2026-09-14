import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateDispatchRequest,
  DispatchCreateResponse,
  Incident,
  IncidentKpis,
  IncidentListFilters,
  PageResponse,
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
}
