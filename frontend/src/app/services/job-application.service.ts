import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { JobApplication } from '../models/job-application';

@Injectable({ providedIn: 'root' })
export class JobApplicationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/applications`;

  getAll(): Observable<JobApplication[]> {
    return this.http.get<JobApplication[]>(this.baseUrl);
  }
}
