import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import {
  ApplicationDetail,
  ApplicationDocument,
  ApplicationListFilters,
  ApplicationSummary,
  ApproveApplicationResponse,
  PageResponse,
} from './admin.models';

/** Client for the MANAGER-only `/api/v1/admin/applications` endpoints. */
@Injectable({ providedIn: 'root' })
export class AdminApplicationService {
  private readonly http = inject(HttpClient);

  list(filters: ApplicationListFilters): Observable<PageResponse<ApplicationSummary>> {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }
    return this.http.get<PageResponse<ApplicationSummary>>('v1/admin/applications', { params });
  }

  get(id: string): Observable<ApplicationDetail> {
    return this.http.get<ApplicationDetail>(`v1/admin/applications/${id}`);
  }

  /**
   * The application a technician's account was created from, so their documents can be reached
   * from their record. A 404 means they were added directly rather than through an application.
   */
  forUser(userId: string): Observable<ApplicationDetail> {
    return this.http.get<ApplicationDetail>(`v1/admin/applications/by-user/${userId}`);
  }

  pendingCount(): Observable<number> {
    return this.http
      .get<{ pending: number }>('v1/admin/applications/pending-count')
      .pipe(map((response) => response.pending));
  }

  /** Provisions the technician's account too, exactly as "Add user" would. */
  approve(id: string): Observable<ApproveApplicationResponse> {
    return this.http.post<ApproveApplicationResponse>(`v1/admin/applications/${id}/approve`, {});
  }

  /** Permanent: the applicant can never apply again. A reason is mandatory. */
  reject(id: string, reason: string): Observable<ApplicationDetail> {
    return this.http.post<ApplicationDetail>(`v1/admin/applications/${id}/reject`, {
      reason: reason.trim(),
    });
  }

  /**
   * Saves an applicant's document to disk. It is fetched with the Manager's token (the endpoint is
   * authenticated, so a plain link wouldn't work) and handed to the browser as a DOWNLOAD — never
   * opened or rendered in the page, because it is an untrusted file from an anonymous source.
   */
  download(applicationId: string, document: ApplicationDocument): Observable<void> {
    return this.http
      .get(`v1/admin/applications/${applicationId}/documents/${document.id}`, {
        responseType: 'blob',
      })
      .pipe(
        map((blob) => {
          const url = URL.createObjectURL(blob);
          const link = window.document.createElement('a');
          link.href = url;
          link.download = document.filename;
          link.rel = 'noopener';
          link.click();
          // After the browser has had a moment to start the download.
          setTimeout(() => URL.revokeObjectURL(url), 10_000);
        }),
      );
  }
}
