import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { EmailCorrection, JobDashboard } from '../models/job-dashboard';

@Injectable({ providedIn: 'root' })
export class JobTrackerService {
  private readonly http = inject(HttpClient);

  /** Organized view from emails analyzed so far (fast, no Outlook/AI calls). */
  getDashboard(): Observable<JobDashboard> {
    return this.http.get<JobDashboard>(`${environment.apiUrl}/dashboard`);
  }

  /**
   * Reads new emails from Inbox + Spam (first time: last 3 months) and starts the AI analysis
   * in the background. Poll getDashboard() while `sync.running` is true.
   */
  sync(): Observable<JobDashboard> {
    return this.http.post<JobDashboard>(`${environment.apiUrl}/sync`, null);
  }

  /** Manually fixes the AI's analysis of one email; returns the updated dashboard. */
  correctEmail(emailId: string, correction: EmailCorrection): Observable<JobDashboard> {
    return this.http.patch<JobDashboard>(
      `${environment.apiUrl}/emails/${encodeURIComponent(emailId)}`,
      correction,
    );
  }
}
