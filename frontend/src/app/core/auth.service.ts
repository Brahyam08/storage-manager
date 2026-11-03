import { HttpClient } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { catchError, switchMap, tap, throwError } from 'rxjs';
import { UserProfile } from './models';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly tokenState = signal<string | null>(null);
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
        tap((user) => this.userState.set(user)),
        catchError((error) => {
          this.clear();
          return throwError(() => error);
        }),
      );
  }

  register(username: string, email: string, password: string) {
    return this.http.post<UserProfile>('/api/auth/register', {
      username: username.trim(),
      email: email.trim(),
      password,
    });
  }

  clear() {
    this.tokenState.set(null);
    this.userState.set(null);
  }
}
