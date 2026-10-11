import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApplicationReceivedResponse, SubmitApplicationRequest } from './apply.models';

/** Client for the public, unauthenticated `POST /api/v1/applications`. */
@Injectable({ providedIn: 'root' })
export class ApplyApiService {
  private readonly http = inject(HttpClient);

  /**
   * Sent as multipart: the details as one JSON part and each proof file as its own part, matched
   * to `request.documents` by position. The JSON part is given an explicit
   * `application/json` type — without it the server can't tell it from a text field.
   */
  submit(
    request: SubmitApplicationRequest,
    files: File[],
  ): Observable<ApplicationReceivedResponse> {
    const body = new FormData();
    body.append('application', new Blob([JSON.stringify(request)], { type: 'application/json' }));
    for (const file of files) {
      body.append('files', file, file.name);
    }
    return this.http.post<ApplicationReceivedResponse>('v1/applications', body);
  }
}
