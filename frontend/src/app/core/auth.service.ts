import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, EMPTY, filter, map, of, switchMap, tap, throwError } from 'rxjs';
import { UserProfile } from './models';

const SESSION_KEY = 'storage-manager.session';
type StorageArea = 'sessionStorage' | 'localStorage';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private remembered = false;
  private revision = 0;
  private readonly tokenState = signal<string | null>(this.readStoredToken());
  private readonly userState = signal<UserProfile | null>(null);
  private readonly warningState = signal('');
  readonly token = this.tokenState.asReadonly();
  readonly user = this.userState.asReadonly();
  readonly storageWarning = this.warningState.asReadonly();

  constructor() {
    if (typeof window === 'undefined') return;
    const changed = (event: StorageEvent) => {
      if (!this.remembered || (event.key !== SESSION_KEY && event.key !== null)) return;
      try {
        if (event.storageArea !== window.localStorage) return;
      } catch {
        return;
      }
      if (this.readStorage('localStorage') !== this.tokenState()) {
        this.clear();
        void this.router.navigate(['/acceso'], { queryParams: { expired: '1' } });
      }
    };
    window.addEventListener('storage', changed);
    this.destroyRef.onDestroy(() => window.removeEventListener('storage', changed));
  }

  login(email: string, password: string, remember = false) {
    this.clear();
    const revision = this.revision;
    return this.http
      .post<{ token: string }>('/api/auth/login', { email: email.trim(), password })
      .pipe(
        switchMap((response) => {
          if (revision !== this.revision) return EMPTY;
          const candidate = response.token;
          this.tokenState.set(candidate);
          return this.http.get<UserProfile>('/api/users/me').pipe(
            filter(() => revision === this.revision && this.tokenState() === candidate),
            tap((user) => {
              this.userState.set(user);
              this.storeToken(candidate, remember);
            }),
          );
        }),
        catchError((error) => {
          if (revision === this.revision) this.clear();
          return throwError(() => error);
        }),
      );
  }

  restore() {
    const token = this.tokenState();
    const revision = this.revision;
    if (!token) return of(false);
    return this.http.get<UserProfile>('/api/users/me').pipe(
      map((user) => {
        if (this.tokenState() !== token || this.revision !== revision) return false;
        this.userState.set(user);
        return true;
      }),
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && this.tokenState() === token && this.revision === revision)
          this.clear();
        return of(false);
      }),
    );
  }

  private readStorage(area: StorageArea): string | null {
    try {
      return window[area].getItem(SESSION_KEY);
    } catch {
      return null;
    }
  }

  private readStoredToken(): string | null {
    const tab = this.readStorage('sessionStorage');
    if (tab) return tab;
    const persistent = this.readStorage('localStorage');
    this.remembered = persistent !== null;
    return persistent;
  }

  private removeStoredToken(area: StorageArea, expected?: string) {
    try {
      if (expected === undefined || window[area].getItem(SESSION_KEY) === expected) {
        window[area].removeItem(SESSION_KEY);
      }
    } catch {
      /* Storage may be disabled. */
    }
  }

  private storeToken(token: string, remember: boolean) {
    const target: StorageArea = remember ? 'localStorage' : 'sessionStorage';
    try {
      window[target].setItem(SESSION_KEY, token);
      this.remembered = remember;
      this.removeStoredToken(remember ? 'sessionStorage' : 'localStorage', token);
    } catch {
      this.remembered = false;
      if (remember) {
        try {
          window.sessionStorage.setItem(SESSION_KEY, token);
        } catch {
          /* Keep memory session. */
        }
      }
      this.warningState.set(
        'No se pudo guardar la sesión en este dispositivo. Puede que debas entrar de nuevo al cerrar la app.',
      );
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
    this.revision++;
    const token = this.tokenState();
    if (token) {
      this.removeStoredToken('sessionStorage', token);
      this.removeStoredToken('localStorage', token);
    }
    this.remembered = false;
    this.tokenState.set(null);
    this.userState.set(null);
    this.warningState.set('');
  }
}
