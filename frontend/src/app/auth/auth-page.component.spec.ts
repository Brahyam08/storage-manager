import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { AuthPageComponent } from './auth-page.component';
import { AuthService } from '../core/auth.service';
import { authInterceptor } from '../core/auth.interceptor';

describe('Registration completion', () => {
  it('keeps a created account and redirects to login if automatic login is rate-limited', () => {
    TestBed.configureTestingModule({
      imports: [AuthPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { routeConfig: { path: 'registro' }, queryParamMap: convertToParamMap({}) },
          },
        },
      ],
    });
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(AuthPageComponent);
    fixture.componentInstance.form.setValue({
      remember: false,
      username: 'Alice',
      email: 'alice@example.test',
      password: 'valid-password',
    });
    fixture.componentInstance.submit();
    http
      .expectOne('/api/auth/register')
      .flush({ id: 1, username: 'Alice', email: 'alice@example.test', role: 'VIEWER' });
    http.expectOne('/api/auth/login').flush({}, { status: 429, statusText: 'Too many requests' });
    expect(router.navigate).toHaveBeenCalledWith(['/acceso'], { queryParams: { registered: '1' } });
    expect(fixture.componentInstance.form.controls.password.value).toBe('');
    expect(TestBed.inject(AuthService).token()).toBeNull();
    http.verify();
    fixture.destroy();
  });
});

describe('Recovering a stored mobile session', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AuthPageComponent> | undefined;
  let router: Router;
  const route = {
    snapshot: {
      routeConfig: { path: 'acceso' },
      queryParamMap: convertToParamMap({}),
    },
  };
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    sessionStorage.setItem('storage-manager.session', 'saved-token');
    TestBed.configureTestingModule({
      imports: [AuthPageComponent],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: route },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => {
    fixture?.destroy();
    fixture = undefined;
    http.verify();
    sessionStorage.clear();
    localStorage.clear();
  });
  function create(returnTo = '/objetos/4?house=1') {
    route.snapshot.queryParamMap = convertToParamMap({ returnTo });
    fixture = TestBed.createComponent(AuthPageComponent);
    fixture.detectChanges();
    return fixture;
  }
  const profile = { id: 7, username: 'Alice', email: 'alice@example.test', role: 'VIEWER' };

  it('retries a connection failure without showing a password form or discarding the token', () => {
    const page = create();
    http.expectOne('/api/users/me').error(new ProgressEvent('error'));
    page.detectChanges();
    expect(page.nativeElement.querySelector('input[type="password"]')).toBeNull();
    expect(page.nativeElement.textContent).toContain('Reintentar conexión');
    expect(sessionStorage.getItem('storage-manager.session')).toBe('saved-token');
    page.componentInstance.resume();
    const retry = http.expectOne('/api/users/me');
    expect(retry.request.headers.get('Authorization')).toBe('Bearer saved-token');
    retry.flush(profile);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/objetos/4?house=1');
    http.expectNone('/api/auth/login');
  });

  it('shows login only after the server rejects the saved session', () => {
    const page = create();
    http.expectOne('/api/users/me').flush({}, { status: 503, statusText: 'Unavailable' });
    page.componentInstance.resume();
    http.expectOne('/api/users/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    page.detectChanges();
    expect(sessionStorage.getItem('storage-manager.session')).toBeNull();
    expect(page.nativeElement.querySelector('input[type="password"]')).not.toBeNull();
    expect(page.nativeElement.textContent).toContain('Tu sesión ha terminado');
  });

  it('never redirects a recovered session to an external return URL', () => {
    create('https://example.test');
    http.expectOne('/api/users/me').flush(profile);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/inventario');
  });
});
