import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { AuthPageComponent } from './auth-page.component';
import { AuthService } from '../core/auth.service';

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
