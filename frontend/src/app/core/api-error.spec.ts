import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { apiError } from './api-error';

describe('API error messages', () => {
  it('uses Retry-After for rate limits', () => {
    expect(
      apiError(
        new HttpErrorResponse({ status: 429, headers: new HttpHeaders({ 'Retry-After': '30' }) }),
      ),
    ).toContain('30 segundos');
  });
  it('does not display internal server error bodies', () => {
    expect(
      apiError(
        new HttpErrorResponse({ status: 500, error: { error: 'private internal details' } }),
      ),
    ).not.toContain('private');
  });
});
