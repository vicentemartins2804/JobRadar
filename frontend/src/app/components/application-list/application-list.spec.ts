import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApplicationList } from './application-list';

describe('ApplicationList', () => {
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ApplicationList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('renders a row per application', async () => {
    const fixture = TestBed.createComponent(ApplicationList);
    fixture.detectChanges();

    httpMock.expectOne('http://localhost:8080/api/applications').flush([
      {
        id: 1,
        company: 'Farfetch',
        role: 'Frontend Developer',
        status: 'INTERVIEW',
        appliedDate: '2026-11-02',
        lastUpdate: '2026-11-18',
        nextStep: null,
      },
    ]);
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
    expect(el.querySelector('.company')?.textContent).toContain('Farfetch');
  });

  it('shows an error when the backend is unreachable', async () => {
    const fixture = TestBed.createComponent(ApplicationList);
    fixture.detectChanges();

    httpMock
      .expectOne('http://localhost:8080/api/applications')
      .error(new ProgressEvent('error'), { status: 0 });
    await fixture.whenStable();

    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.state--error')).toBeTruthy();
  });
});
