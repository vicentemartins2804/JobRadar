import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { JobDashboard } from '../../models/job-dashboard';
import { AUTO_SYNC_INTERVAL_MS, AUTO_SYNC_STORAGE_KEY, Dashboard, POLL_INTERVAL_MS } from './dashboard';

const API = 'http://localhost:8080/api';

const synced: JobDashboard = {
  lastSyncAt: '2026-11-23T10:00:00Z',
  emailsAnalyzed: 42,
  sync: { running: false, total: 42, analyzed: 42, failed: 0 },
  interviews: [
    {
      emailId: 'INT-1',
      company: 'Farfetch',
      role: 'Frontend Developer',
      interviewAt: '2026-11-26T14:00',
      nextStep: 'Prepare system design',
      summary: 'Technical interview invite',
      receivedAt: '2026-11-20T09:30:00Z',
      folder: 'JUNK',
    },
  ],
  applications: [
    {
      company: 'Talkdesk',
      role: 'Java Engineer',
      status: 'APPLICATION_CONFIRMATION',
      firstSeenAt: '2026-11-10T10:00:00Z',
      lastUpdateAt: '2026-11-10T10:00:00Z',
      nextStep: '',
      summary: 'Interview scheduled',
      emailCount: 2,
      foundInJunk: false,
      timeline: [
        {
          emailId: 'APP-1',
          at: '2026-11-10T10:00:00Z',
          status: 'APPLICATION_CONFIRMATION',
          company: 'Talkdesk',
          role: 'Java Engineer',
          summary: 'Application received',
          emailSubject: 'Thanks for applying',
          folder: 'INBOX',
          corrected: false,
        },
        {
          emailId: 'APP-2',
          at: '2026-11-15T10:00:00Z',
          status: 'INTERVIEW',
          company: 'Talkdesk',
          role: '',
          summary: 'Invited to a technical interview',
          emailSubject: 'Interview invitation',
          folder: 'JUNK',
          corrected: true,
        },
      ],
    },
  ],
  openings: [
    {
      emailId: 'ALERT-1',
      company: 'OutSystems',
      role: 'Full Stack Dev',
      location: 'Remote',
      url: 'https://jobs.example/1',
      receivedAt: '2026-11-21T10:00:00Z',
      folder: 'INBOX',
      emailSubject: 'New jobs for you',
    },
    {
      emailId: 'ALERT-2',
      company: 'Evil',
      role: 'Bad link',
      location: '',
      url: 'javascript:alert(1)',
      receivedAt: '2026-11-21T10:00:00Z',
      folder: 'INBOX',
      emailSubject: 'x',
    },
  ],
};

describe('Dashboard', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Dashboard],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.removeItem(AUTO_SYNC_STORAGE_KEY);
  });

  it('hides an opening by marking its email as not job related', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(`${API}/dashboard`).flush(synced);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    el.querySelector<HTMLButtonElement>('.openings .hide')!.click();

    const req = httpMock.expectOne(`${API}/emails/ALERT-1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ category: 'NOT_JOB_RELATED' });
    req.flush({ ...synced, openings: synced.openings.slice(1) });
    await fixture.whenStable();

    expect(el.querySelectorAll('.openings li').length).toBe(1);
  });

  it('auto-syncs every 15 minutes unless turned off', async () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(Dashboard);
      fixture.detectChanges();
      httpMock.expectOne(`${API}/dashboard`).flush(synced);
      fixture.detectChanges();

      await vi.advanceTimersByTimeAsync(AUTO_SYNC_INTERVAL_MS);
      httpMock.expectOne(`${API}/sync`).flush(synced);

      const checkbox = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('.auto-sync input')!;
      expect(checkbox.checked).toBe(true);
      checkbox.click();
      expect(localStorage.getItem(AUTO_SYNC_STORAGE_KEY)).toBe('false');

      await vi.advanceTimersByTimeAsync(AUTO_SYNC_INTERVAL_MS);
      httpMock.expectNone(`${API}/sync`);
      fixture.destroy();
    } finally {
      vi.useRealTimers();
    }
  });

  it('renders interviews, applications and openings', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(`${API}/dashboard`).flush(synced);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.card')?.textContent).toContain('Farfetch');
    expect(el.querySelector('.card .tag--junk')).toBeTruthy();
    expect(el.querySelector('tbody tr')?.textContent).toContain('Talkdesk');
    expect(el.querySelector('.badge')?.textContent).toContain('Applied');
    expect(el.querySelectorAll('.openings li').length).toBe(2);
    expect(el.querySelectorAll('.openings a').length).toBe(1);
    expect(el.querySelector('.sync-info')?.textContent).toContain('42 emails analyzed');
  });

  it('expands an application to show its progress and email history', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(`${API}/dashboard`).flush(synced);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.history-row')).toBeNull();

    el.querySelector<HTMLTableRowElement>('.app-row')!.click();
    fixture.detectChanges();

    expect(el.querySelector('.expand')?.getAttribute('aria-expanded')).toBe('true');
    const stages = Array.from(el.querySelectorAll('.stage')).map((s) => s.className);
    expect(stages).toEqual(['stage stage--done', 'stage stage--skipped', 'stage stage--current', 'stage stage--todo']);
    const events = el.querySelectorAll('.timeline .event');
    expect(events.length).toBe(2);
    expect(events[0].textContent).toContain('Application received');
    expect(events[1].textContent).toContain('Invited to a technical interview');
    expect(events[1].querySelector('.tag--junk')).toBeTruthy();

    el.querySelector<HTMLButtonElement>('.expand')!.click();
    fixture.detectChanges();
    expect(el.querySelector('.history-row')).toBeNull();
  });

  it('syncs automatically on first visit', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock
      .expectOne(`${API}/dashboard`)
      .flush({ ...synced, lastSyncAt: null, interviews: [], applications: [], openings: [] });

    const sync = httpMock.expectOne(`${API}/sync`);
    expect(sync.request.method).toBe('POST');
    sync.flush(synced);
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.card')).toBeTruthy();
  });

  it('shows progress and polls until the background analysis finishes', async () => {
    vi.useFakeTimers();
    try {
      const fixture = TestBed.createComponent(Dashboard);
      fixture.detectChanges();
      httpMock
        .expectOne(`${API}/dashboard`)
        .flush({ ...synced, sync: { running: true, total: 10, analyzed: 4, failed: 1 } });
      fixture.detectChanges();

      const el = fixture.nativeElement as HTMLElement;
      expect(el.querySelector('.sync-info')?.textContent).toContain('5 of 10');
      expect(el.querySelector('.progress-bar')?.getAttribute('style')).toContain('50%');
      expect(el.querySelector<HTMLButtonElement>('.toolbar button')!.disabled).toBe(true);

      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS);
      httpMock
        .expectOne(`${API}/dashboard`)
        .flush({ ...synced, sync: { running: false, total: 10, analyzed: 9, failed: 1 } });
      fixture.detectChanges();

      expect(el.querySelector('.progress')).toBeNull();
      expect(el.querySelector('.sync-info')?.textContent).toContain('1 failed, will retry');

      // Finished: no more polling
      await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3);
      httpMock.expectNone(`${API}/dashboard`);
    } finally {
      vi.useRealTimers();
    }
  });

  it('does not sync automatically when already synced', () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(`${API}/dashboard`).flush(synced);

    httpMock.expectNone(`${API}/sync`);
  });

  it('shows an error when sync fails', async () => {
    const fixture = TestBed.createComponent(Dashboard);
    fixture.detectChanges();
    httpMock.expectOne(`${API}/dashboard`).flush(synced);
    await fixture.whenStable();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.toolbar button')!.click();
    httpMock.expectOne(`${API}/sync`).flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect((fixture.nativeElement as HTMLElement).querySelector('.error')?.textContent).toContain('Sync failed');
  });
});
