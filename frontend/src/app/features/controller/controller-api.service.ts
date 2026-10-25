import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  CreateDispatchRequest,
  CreateIncidentRequest,
  DispatchAnswer,
  DispatchCreateResponse,
  FreshdeskStatus,
  FreshdeskUpdate,
  Incident,
  IncidentKpis,
  IncidentListFilters,
  IncidentLock,
  IncidentEvent,
  IncidentProgress,
  JobCounts,
  JobDetail,
  JobStage,
  JobStatus,
  JobSummary,
  PageResponse,
  Report,
  ResendResult,
  SimulatedTicket,
  TechnicianAvailability,
  TechnicianCandidate,
  TechnicianRosterEntry,
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

  /** What the incident's Freshdesk ticket has been told (or, in demo mode, would be told). */
  getFreshdeskUpdates(id: string): Observable<FreshdeskUpdate[]> {
    return this.http.get<FreshdeskUpdate[]>(`v1/controller/incidents/${id}/freshdesk-updates`);
  }

  /** The ticket's history, oldest first: who logged, dispatched, nudged, answered and closed it. */
  getIncidentEvents(id: string): Observable<IncidentEvent[]> {
    return this.http.get<IncidentEvent[]>(`v1/controller/incidents/${id}/events`);
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

  // --- jobs (FR-05–FR-07) ------------------------------------------------------------------

  listJobs(
    stage: JobStage,
    q: string,
    page: number,
    size: number,
  ): Observable<PageResponse<JobSummary>> {
    let params = new HttpParams().set('stage', stage).set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    return this.http.get<PageResponse<JobSummary>>('v1/controller/jobs', { params });
  }

  jobCounts(): Observable<JobCounts> {
    return this.http.get<JobCounts>('v1/controller/jobs/counts');
  }

  getJob(id: string): Observable<JobDetail> {
    return this.http.get<JobDetail>(`v1/controller/jobs/${id}`);
  }

  /** Signs off a submitted job; its incident closes with it. 409 unless it is submitted. */
  closeJob(id: string, closingNotes: string | null): Observable<JobDetail> {
    return this.http.post<JobDetail>(`v1/controller/jobs/${id}/close`, { closingNotes });
  }

  /** Demo only: moves a job on as the technician who holds it. */
  simulateJobStatus(id: string, status: JobStatus, reason: string | null): Observable<JobDetail> {
    return this.http.post<JobDetail>(`v1/controller/simulation/jobs/${id}/status`, {
      status,
      reason,
    });
  }

  /** Demo only: submits a plausible report for a completed job, as its technician. */
  simulateJobReport(id: string): Observable<JobDetail> {
    return this.http.post<JobDetail>(`v1/controller/simulation/jobs/${id}/report`, null);
  }

  // --- reports and roster -------------------------------------------------------------------

  report(from: string, to: string, zone: string): Observable<Report> {
    const params = new HttpParams().set('from', from).set('to', to).set('zone', zone);
    return this.http.get<Report>('v1/controller/reports/summary', { params });
  }

  /** The CSV as a file; the bearer token rides on the request, so this is not a plain link. */
  reportCsv(from: string, to: string, zone: string): Observable<Blob> {
    const params = new HttpParams().set('from', from).set('to', to).set('zone', zone);
    return this.http.get('v1/controller/reports/incidents.csv', { params, responseType: 'blob' });
  }

  listTechnicians(
    q: string,
    availability: TechnicianAvailability | '',
    page: number,
    size: number,
  ): Observable<PageResponse<TechnicianRosterEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (q.trim()) {
      params = params.set('q', q.trim());
    }
    if (availability) {
      params = params.set('availability', availability);
    }
    return this.http.get<PageResponse<TechnicianRosterEntry>>('v1/controller/technicians', {
      params,
    });
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
