import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Application, JobDashboard } from '../../models/job-dashboard';
import { ApplicationHistory } from './application-history';

const app: Application = {
  company: 'Farfetch',
  role: 'Frontend Developer',
  status: 'INTERVIEW',
  firstSeenAt: '2026-11-01T10:00:00Z',
  lastUpdateAt: '2026-11-05T10:00:00Z',
  nextStep: '',
  summary: '',
  emailCount: 1,
  foundInJunk: false,
  timeline: [
    {
      emailId: 'E1',
      at: '2026-11-05T10:00:00Z',
      status: 'INTERVIEW',
      company: 'Farfetch Portugal',
      role: '',
      summary: 'Interview invite',
      emailSubject: 'Interview',
      folder: 'INBOX',
      corrected: false,
    },
  ],
};

describe('ApplicationHistory', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApplicationHistory],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('edits an email and emits the updated dashboard', () => {
    const fixture = TestBed.createComponent(ApplicationHistory);
    fixture.componentRef.setInput('app', app);
    const emitted: JobDashboard[] = [];
    fixture.componentInstance.changed.subscribe((d) => emitted.push(d));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    el.querySelector<HTMLButtonElement>('.link')!.click();
    fixture.detectChanges();

    // Form starts with the email's current values
    const [company, role] = Array.from(el.querySelectorAll<HTMLInputElement>('.edit input'));
    expect(company.value).toBe('Farfetch Portugal');
    const select = el.querySelector<HTMLSelectElement>('.edit select')!;
    expect(select.value).toBe('INTERVIEW');

    company.value = 'Farfetch';
    company.dispatchEvent(new Event('input'));
    role.value = 'Frontend Developer';
    role.dispatchEvent(new Event('input'));
    select.value = 'OFFER';
    select.dispatchEvent(new Event('change'));
    el.querySelector<HTMLFormElement>('.edit')!.dispatchEvent(new Event('submit'));

    const req = httpMock.expectOne('http://localhost:8080/api/emails/E1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ category: 'OFFER', company: 'Farfetch', role: 'Frontend Developer' });
    req.flush({ emailsAnalyzed: 1 } as JobDashboard);
    fixture.detectChanges();

    expect(emitted).toHaveLength(1);
    expect(el.querySelector('.edit')).toBeNull();
  });

  it('keeps the form open and shows an error if saving fails', () => {
    const fixture = TestBed.createComponent(ApplicationHistory);
    fixture.componentRef.setInput('app', app);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;

    el.querySelector<HTMLButtonElement>('.link')!.click();
    fixture.detectChanges();
    el.querySelector<HTMLFormElement>('.edit')!.dispatchEvent(new Event('submit'));
    httpMock.expectOne('http://localhost:8080/api/emails/E1').flush(null, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    expect(el.querySelector('.edit')).toBeTruthy();
    expect(el.querySelector('.save-error')).toBeTruthy();
  });

  it('marks corrected emails as edited', () => {
    const fixture = TestBed.createComponent(ApplicationHistory);
    fixture.componentRef.setInput('app', {
      ...app,
      timeline: [{ ...app.timeline[0], corrected: true }],
    });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.tag--edited')).toBeTruthy();
  });
});
