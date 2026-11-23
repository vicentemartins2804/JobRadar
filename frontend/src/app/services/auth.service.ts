import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { environment } from '../../environments/environment';
import { User } from '../models/user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  /** undefined = not checked yet, null = not logged in */
  readonly user = signal<User | null | undefined>(undefined);

  /** Asks the backend who is logged in (based on the session cookie). */
  loadUser(): Observable<User | null> {
    const cached = this.user();
    if (cached !== undefined) {
      return of(cached);
    }
    return this.http.get<User>(`${environment.apiUrl}/me`).pipe(
      catchError(() => of(null)),
      tap((user) => this.user.set(user)),
    );
  }

  isLoggedIn(): Observable<boolean> {
    return this.loadUser().pipe(map((user) => user !== null));
  }

  /** Full page redirect: the backend sends the browser to the Microsoft login page. */
  login(): void {
    window.location.href = `${environment.backendUrl}/oauth2/authorization/microsoft`;
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${environment.apiUrl}/logout`, null).pipe(
      tap(() => this.user.set(null)),
    );
  }
}
