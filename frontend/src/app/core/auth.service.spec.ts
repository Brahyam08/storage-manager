import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  provideRouter,
  Router,
  RouterStateSnapshot,
} from '@angular/router';
import { AuthService } from './auth.service';
import { authInterceptor } from './auth.interceptor';
import { authGuard } from './auth.guard';
import { isObservable } from 'rxjs';

const profile = { id: 7, username: 'Alice', email: 'alice@example.test', role: 'VIEWER' } as const;

describe('Authentication boundaries', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
  });
  afterEach(() => {
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });

  function signIn() {
    auth.login(' alice@example.test ', '  password  ').subscribe();
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ email: 'alice@example.test', password: '  password  ' });
    login.flush({ token: 'test-token' });
    const me = http.expectOne('/api/users/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer test-token');
    me.flush(profile);
  }

  it('remembers only a verified token and restores it after the tab storage is gone', () => {
    auth.login('alice@example.test', 'password', true).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'remembered-token' });
    expect(localStorage.getItem('storage-manager.session')).toBeNull();
    http.expectOne('/api/users/me').flush(profile);
    expect(localStorage.getItem('storage-manager.session')).toBe('remembered-token');
    expect(sessionStorage.getItem('storage-manager.session')).toBeNull();
    const reloaded = TestBed.runInInjectionContext(() => new AuthService());
    expect(reloaded.user()).toBeNull();
    expect(reloaded.token()).toBe('remembered-token');
    reloaded.restore().subscribe();
    http.expectOne('/api/users/me').flush(profile);
    expect(reloaded.user()?.id).toBe(7);
    reloaded.clear();
    expect(localStorage.getItem('storage-manager.session')).toBeNull();
  });

  it('keeps a remembered token on a transient failure and removes it on a confirmed 401', () => {
    auth.login('alice@example.test', 'password', true).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'remembered-token' });
    http.expectOne('/api/users/me').flush(profile);
    auth.restore().subscribe();
    http.expectOne('/api/users/me').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(localStorage.getItem('storage-manager.session')).toBe('remembered-token');
    auth.restore().subscribe();
    http.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem('storage-manager.session')).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('does not erase a newer persistent token when an older tab receives a 401', () => {
    auth.login('alice@example.test', 'password', true).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'older-token' });
    http.expectOne('/api/users/me').flush(profile);
    TestBed.inject(HttpClient)
      .get('/api/houses')
      .subscribe({ error: () => {} });
    const pending = http.expectOne('/api/houses');
    localStorage.setItem('storage-manager.session', 'newer-other-tab-token');
    pending.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(localStorage.getItem('storage-manager.session')).toBe('newer-other-tab-token');
    expect(auth.token()).toBeNull();
  });

  it('invalidates a remembered window on a storage change without switching its account', () => {
    auth.login('alice@example.test', 'password', true).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'older-token' });
    http.expectOne('/api/users/me').flush(profile);
    localStorage.setItem('storage-manager.session', 'newer-token');
    window.dispatchEvent(
      new StorageEvent('storage', {
        storageArea: localStorage,
        key: 'storage-manager.session',
        oldValue: 'older-token',
        newValue: 'newer-token',
      }),
    );
    expect(auth.user()).toBeNull();
    expect(auth.token()).toBeNull();
    expect(localStorage.getItem('storage-manager.session')).toBe('newer-token');
    expect(router.navigate).toHaveBeenCalledWith(['/acceso'], { queryParams: { expired: '1' } });
  });

  it('keeps a different remembered login while finishing a temporary login', () => {
    auth.login('alice@example.test', 'password', false).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'temporary-token' });
    const pending = http.expectOne('/api/users/me');
    localStorage.setItem('storage-manager.session', 'other-window-token');
    pending.flush(profile);
    expect(sessionStorage.getItem('storage-manager.session')).toBe('temporary-token');
    expect(localStorage.getItem('storage-manager.session')).toBe('other-window-token');
  });

  it('synchronizes clearing all persistent storage for a remembered session', () => {
    auth.login('alice@example.test', 'password', true).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'remembered-token' });
    http.expectOne('/api/users/me').flush(profile);
    localStorage.clear();
    window.dispatchEvent(new StorageEvent('storage', { storageArea: localStorage, key: null }));
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('keeps a temporary tab independent from persistent changes in another window', () => {
    signIn();
    localStorage.setItem('storage-manager.session', 'other-token');
    window.dispatchEvent(
      new StorageEvent('storage', {
        storageArea: localStorage,
        key: 'storage-manager.session',
        newValue: 'other-token',
      }),
    );
    expect(auth.token()).toBe('test-token');
    expect(auth.user()?.id).toBe(7);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('does not resurrect a login when logout occurs before its pending profile response', () => {
    const signedIn = vi.fn();
    auth.login('alice@example.test', 'password', true).subscribe(signedIn);
    http.expectOne('/api/auth/login').flush({ token: 'candidate-token' });
    const pending = http.expectOne('/api/users/me');
    auth.clear();
    pending.flush(profile);
    expect(signedIn).not.toHaveBeenCalled();
    expect(auth.user()).toBeNull();
    expect(localStorage.getItem('storage-manager.session')).toBeNull();
  });

  it('does not resurrect a login response that arrives after logout', () => {
    auth.login('alice@example.test', 'password', true).subscribe();
    const pending = http.expectOne('/api/auth/login');
    auth.clear();
    pending.flush({ token: 'late-token' });
    http.expectNone('/api/users/me');
    expect(auth.token()).toBeNull();
  });

  it('keeps a working memory session and reports blocked storage', () => {
    const blocked = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    try {
      auth.login('alice@example.test', 'password', true).subscribe();
      http.expectOne('/api/auth/login').flush({ token: 'memory-token' });
      http.expectOne('/api/users/me').flush(profile);
      expect(auth.token()).toBe('memory-token');
      expect(auth.storageWarning()).not.toBe('');
    } finally {
      blocked.mockRestore();
    }
  });

  it('loads identity from the backend and clears it on logout', () => {
    signIn();
    expect(auth.user()).toEqual(profile);
    expect(auth.token()).toBe('test-token');
    auth.clear();
    expect(auth.user()).toBeNull();
    expect(auth.token()).toBeNull();
  });

  it('restores a tab session using a fresh server profile after reload', () => {
    signIn();
    expect(sessionStorage.getItem('storage-manager.session')).toBe('test-token');
    const reloaded = TestBed.runInInjectionContext(() => new AuthService());
    expect(reloaded.user()).toBeNull();
    let restored: boolean | undefined;
    reloaded.restore().subscribe((value) => (restored = value));
    http.expectOne('/api/users/me').flush(profile);
    expect(restored).toBe(true);
    expect(reloaded.user()).toEqual(profile);
    reloaded.clear();
    expect(sessionStorage.getItem('storage-manager.session')).toBeNull();
  });

  it('rejects an expired stored session instead of trusting cached identity', () => {
    sessionStorage.setItem('storage-manager.session', 'expired-token');
    const reloaded = TestBed.runInInjectionContext(() => new AuthService());
    let restored: boolean | undefined;
    reloaded.restore().subscribe((value) => (restored = value));
    http.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(restored).toBe(false);
    expect(reloaded.user()).toBeNull();
    expect(sessionStorage.getItem('storage-manager.session')).toBeNull();
  });

  it('does not send credentials outside the API or expire the session on an external error', () => {
    signIn();
    TestBed.inject(HttpClient)
      .get('https://example.test/data')
      .subscribe({ error: () => {} });
    const external = http.expectOne('https://example.test/data');
    expect(external.request.headers.has('Authorization')).toBe(false);
    external.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.user()).toEqual(profile);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('expires a rejected API session and redirects to login', () => {
    signIn();
    TestBed.inject(HttpClient)
      .get('/api/houses')
      .subscribe({ error: () => {} });
    http.expectOne('/api/houses').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
    expect(router.navigate).toHaveBeenCalledWith(['/acceso'], { queryParams: { expired: '1' } });
  });

  it('does not keep a token if loading the profile fails', () => {
    auth.login('alice@example.test', 'password').subscribe({ error: () => {} });
    http.expectOne('/api/auth/login').flush({ token: 'candidate-token' });
    http.expectOne('/api/users/me').flush({}, { status: 500, statusText: 'Server error' });
    expect(auth.token()).toBeNull();
    expect(auth.user()).toBeNull();
  });

  it('guards inventory routes using verified identity', () => {
    const guard = () =>
      TestBed.runInInjectionContext(() =>
        authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
      );
    expect(router.serializeUrl(guard() as ReturnType<Router['createUrlTree']>)).toBe('/acceso');
    signIn();
    expect(guard()).toBe(true);
  });

  it('waits for server verification before allowing a restored session into inventory', () => {
    signIn();
    vi.spyOn(auth, 'user').mockReturnValue(null);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, {} as RouterStateSnapshot),
    );
    expect(isObservable(result)).toBe(true);
    if (!isObservable(result)) throw new Error('Expected an asynchronous guard');
    let allowed: unknown;
    result.subscribe((value) => (allowed = value));
    expect(allowed).toBeUndefined();
    const request = http.expectOne('/api/users/me');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush(profile);
    expect(allowed).toBe(true);
  });

  it('preserves a stored token and return route when restoration fails temporarily', () => {
    signIn();
    vi.spyOn(auth, 'user').mockReturnValue(null);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/objetos/4?house=1' } as RouterStateSnapshot),
    );
    if (!isObservable(result)) throw new Error('Expected an asynchronous guard');
    let redirect = '';
    result.subscribe((value) => {
      redirect = router.serializeUrl(value as ReturnType<Router['createUrlTree']>);
    });
    http.expectOne('/api/users/me').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(auth.token()).toBe('test-token');
    expect(sessionStorage.getItem('storage-manager.session')).toBe('test-token');
    expect(redirect).toContain('/acceso?returnTo=');
    expect(redirect).not.toContain('expired');
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('keeps the expired-session message when the guard receives a 401', () => {
    signIn();
    vi.spyOn(auth, 'user').mockReturnValue(null);
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/inventario' } as RouterStateSnapshot),
    );
    if (!isObservable(result)) throw new Error('Expected an asynchronous guard');
    let redirect = '';
    result.subscribe((value) => {
      redirect = router.serializeUrl(value as ReturnType<Router['createUrlTree']>);
    });
    http.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(redirect).toBe('/acceso?expired=1');
    expect(sessionStorage.getItem('storage-manager.session')).toBeNull();
  });

  it('does not let an old rejected request clear a newer session', () => {
    signIn();
    TestBed.inject(HttpClient)
      .get('/api/houses')
      .subscribe({ error: () => {} });
    const oldRequest = http.expectOne('/api/houses');
    auth.login('alice@example.test', 'password').subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'new-token' });
    http.expectOne('/api/users/me').flush(profile);
    oldRequest.flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.token()).toBe('new-token');
    expect(auth.user()).toEqual(profile);
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
