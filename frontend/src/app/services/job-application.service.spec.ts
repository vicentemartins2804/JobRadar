import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { JobApplication } from '../models/job-application';
import { JobApplicationService } from './job-application.service';

describe('JobApplicationService', () => {
  let service: JobApplicationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(JobApplicationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches applications from the backend', () => {
    const mock: JobApplication[] = [
      {
        id: 1,
        company: 'Farfetch',
        role: 'Frontend Developer',
        status: 'INTERVIEW',
        appliedDate: '2026-11-02',
        lastUpdate: '2026-11-18',
        nextStep: 'Technical interview on 26 Nov',
      },
    ];

    let result: JobApplication[] | undefined;
    service.getAll().subscribe((apps) => (result = apps));

    const req = httpMock.expectOne('http://localhost:8080/api/applications');
    expect(req.request.method).toBe('GET');
    req.flush(mock);

    expect(result).toEqual(mock);
  });
});
