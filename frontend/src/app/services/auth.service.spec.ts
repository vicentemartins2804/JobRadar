import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('stores the user when the session is valid', () => {
    let loggedIn: boolean | undefined;
    service.isLoggedIn().subscribe((value) => (loggedIn = value));

    httpMock
      .expectOne('http://localhost:8080/api/me')
      .flush({ name: 'Vicente', email: 'vicente@example.com' });

    expect(loggedIn).toBe(true);
    expect(service.user()?.name).toBe('Vicente');
  });

  it('treats 401 as logged out', () => {
    let loggedIn: boolean | undefined;
    service.isLoggedIn().subscribe((value) => (loggedIn = value));

    httpMock
      .expectOne('http://localhost:8080/api/me')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(loggedIn).toBe(false);
    expect(service.user()).toBeNull();
  });

  it('only calls /api/me once', () => {
    service.loadUser().subscribe();
    httpMock.expectOne('http://localhost:8080/api/me').flush({ name: 'V', email: 'v@x.pt' });

    service.loadUser().subscribe();
    httpMock.expectNone('http://localhost:8080/api/me');
  });

  it('clears the user on logout', () => {
    service.user.set({ name: 'V', email: 'v@x.pt' });
    service.logout().subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(service.user()).toBeNull();
  });
});
