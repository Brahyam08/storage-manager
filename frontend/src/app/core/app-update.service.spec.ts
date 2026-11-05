import { DOCUMENT } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AppUpdateService } from './app-update.service';

const page = (main = 'main-OLD123.js', css = 'styles-OLD123.css') =>
  `<html><head><link rel="stylesheet" href="${css}"></head><body><app-root></app-root><script src="${main}"></script></body></html>`;

describe('App update notice', () => {
  let service: AppUpdateService;
  let http: HttpTestingController;
  beforeEach(() => {
    const document = new DOMParser().parseFromString(page(), 'text/html');
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: DOCUMENT, useValue: document },
      ],
    });
    service = TestBed.inject(AppUpdateService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
  });

  it('keeps the notice hidden for the loaded version', () => {
    service.check();
    const request = http.expectOne('/index.html');
    expect(request.request.headers.get('Cache-Control')).toBe('no-cache');
    request.flush(page());
    expect(service.available()).toBe(false);
  });

  it('compares the first remote response with the already loaded document', () => {
    service.check();
    http.expectOne('/index.html').flush(page('main-NEW123.js'));
    expect(service.available()).toBe(true);
    service.check();
    http.expectNone('/index.html');
  });

  it('detects a stylesheet change without changing scripts', () => {
    service.check();
    http.expectOne('/index.html').flush(page('main-OLD123.js', 'styles-NEW123.css'));
    expect(service.available()).toBe(true);
  });

  it('ignores error pages and retries network failures without showing a false notice', () => {
    service.check();
    http.expectOne('/index.html').flush('<h1>Proxy unavailable</h1>');
    expect(service.available()).toBe(false);
    service.check();
    http.expectOne('/index.html').flush('', { status: 503, statusText: 'Unavailable' });
    expect(service.available()).toBe(false);
    service.check();
    http.expectOne('/index.html').flush(page('main-NEW123.js'));
    expect(service.available()).toBe(true);
  });

  it('prevents overlapping checks and cancels the request on destruction', () => {
    service.check();
    const pending = http.expectOne('/index.html');
    service.check();
    http.expectNone('/index.html');
    TestBed.resetTestingModule();
    expect(pending.cancelled).toBe(true);
  });
});
