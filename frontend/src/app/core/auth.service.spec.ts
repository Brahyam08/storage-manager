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

const profile = { id: 7, username: 'Alice', email: 'alice@example.test', role: 'VIEWER' } as const;

describe('Authentication boundaries', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  let router: Router;

  beforeEach(() => {
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
  afterEach(() => http.verify());

  function signIn() {
    auth.login(' alice@example.test ', '  password  ').subscribe();
    const login = http.expectOne('/api/auth/login');
    expect(login.request.body).toEqual({ email: 'alice@example.test', password: '  password  ' });
    login.flush({ token: 'test-token' });
    const me = http.expectOne('/api/users/me');
    expect(me.request.headers.get('Authorization')).toBe('Bearer test-token');
    me.flush(profile);
  }

  it('loads identity from the backend and clears it on logout', () => {
    signIn();
    expect(auth.user()).toEqual(profile);
    expect(auth.token()).toBe('test-token');
    auth.clear();
    expect(auth.user()).toBeNull();
    expect(auth.token()).toBeNull();
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
