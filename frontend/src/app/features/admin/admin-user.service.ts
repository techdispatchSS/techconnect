import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AdminUser,
  AuditEntry,
  AuditFilters,
  CreateUserRequest,
  CreateUserResponse,
  InviteResponse,
  PageResponse,
  UpdateUserRequest,
  UserListFilters,
} from './admin.models';

/** Client for the MANAGER-only `/api/v1/admin` endpoints (M-06). */
@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private readonly http = inject(HttpClient);

  /**
   * Filtering, searching and paging are all delegated to the server. Loading the whole
   * roster and filtering in the browser would stop scaling the moment the technician list
   * grows, and would leak deactivated accounts into memory unnecessarily.
   */
  list(filters: UserListFilters): Observable<PageResponse<AdminUser>> {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);

    if (filters.role) {
      params = params.set('role', filters.role);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }

    return this.http.get<PageResponse<AdminUser>>('v1/admin/users', { params });
  }

  get(id: string): Observable<AdminUser> {
    return this.http.get<AdminUser>(`v1/admin/users/${id}`);
  }

  create(request: CreateUserRequest): Observable<CreateUserResponse> {
    return this.http.post<CreateUserResponse>('v1/admin/users', request);
  }

  update(id: string, request: UpdateUserRequest): Observable<AdminUser> {
    return this.http.put<AdminUser>(`v1/admin/users/${id}`, request);
  }

  /** Offboarding — a soft disable that also revokes the user's active session. */
  deactivate(id: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`v1/admin/users/${id}/deactivate`, {});
  }

  reactivate(id: string): Observable<AdminUser> {
    return this.http.post<AdminUser>(`v1/admin/users/${id}/reactivate`, {});
  }

  /** Issues a fresh link, which also invalidates whatever was sent previously. */
  resendInvite(id: string): Observable<InviteResponse> {
    return this.http.post<InviteResponse>(`v1/admin/users/${id}/resend-invite`, {});
  }

  /** All audit filtering is server-side — an append-only log cannot be paged into memory. */
  audit(filters: AuditFilters): Observable<PageResponse<AuditEntry>> {
    let params = new HttpParams().set('page', filters.page).set('size', filters.size);

    if (filters.targetUserId) {
      params = params.set('targetUserId', filters.targetUserId);
    }
    if (filters.action) {
      params = params.set('action', filters.action);
    }
    if (filters.q.trim()) {
      params = params.set('q', filters.q.trim());
    }
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }

    return this.http.get<PageResponse<AuditEntry>>('v1/admin/audit', { params });
  }
}
