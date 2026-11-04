import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, map, of, switchMap, tap, throwError } from 'rxjs';
import { UserProfile } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(this.readStoredToken());
  private readonly userState = signal<UserProfile | null>(null);
  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();

  login(email: string, password: string) {
    this.clear();
    return this.http
      .post<{ token: string }>('/api/auth/login', { email: email.trim(), password })
      .pipe(
        tap((response) => this.tokenState.set(response.token)),
        switchMap(() => this.http.get<UserProfile>('/api/users/me')),
        tap((user) => {
          this.userState.set(user);
          this.storeToken(this.tokenState());
        }),
        catchError((error) => {
          this.clear();
          return throwError(() => error);
        }),
      );
  }

  restore() {
    const token = this.tokenState();
    if (!token) return of(false);
    return this.http.get<UserProfile>('/api/users/me').pipe(
      map((user) => {
        if (this.tokenState() !== token) return false;
        this.userState.set(user);
        return true;
      }),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && this.tokenState() === token) this.clear();
        return of(false);
      }),
    );
  }

  private readStoredToken(): string | null {
    try {
      return sessionStorage.getItem('storage-manager.session');
    } catch {
      return null;
    }
  }

  private storeToken(token: string | null) {
    try {
      if (token) sessionStorage.setItem('storage-manager.session', token);
      else sessionStorage.removeItem('storage-manager.session');
    } catch {
      // Storage may be disabled; the current in-memory session still works.
    }
  }

  register(username: string, email: string, password: string) {
    return this.http.post<UserProfile>('/api/auth/register', {
      username: username.trim(),
      email: email.trim(),
      password,
    });
  }

  clear() {
    this.storeToken(null);
    this.tokenState.set(null);
    this.userState.set(null);
  }
}
