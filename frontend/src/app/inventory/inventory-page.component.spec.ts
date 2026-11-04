import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { InventoryPageComponent } from './inventory-page.component';

const houses = [
  { id: 1, name: 'First', ownerId: 7 },
  { id: 2, name: 'Second', ownerId: 7 },
];

describe('House switching', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<InventoryPageComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [InventoryPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user: signal({ id: 7, username: 'Alice', email: 'alice@example.test', role: 'VIEWER' }),
            clear: vi.fn(),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventoryPageComponent);
    http.expectOne('/api/houses').flush(houses);
    fixture.detectChanges();
    http.expectOne((request) => request.url === '/api/locations').flush([]);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('cancels data from the previous house before loading the next', async () => {
    const first = await vi.waitFor(() =>
      http.expectOne(
        (request) => request.url === '/api/items' && request.params.get('houseId') === '1',
      ),
    );
    fixture.componentInstance.houseControl.setValue(2);
    fixture.detectChanges();
    http
      .expectOne(
        (request) => request.url === '/api/locations' && request.params.get('houseId') === '2',
      )
      .flush([]);
    expect(first.cancelled).toBe(true);
    http
      .expectOne((request) => request.url === '/api/items' && request.params.get('houseId') === '2')
      .flush([]);
    expect(fixture.componentInstance.selectedHouse()?.id).toBe(2);
    expect(fixture.componentInstance.items()).toEqual([]);
  });

  it('retries a failed inventory load without resetting the selected filter', async () => {
    const first = await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'));
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.componentInstance.filter.set('private');
    fixture.detectChanges();
    const retry = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button) => (button as HTMLButtonElement).textContent?.trim() === 'Reintentar',
    ) as HTMLButtonElement;
    expect(retry).toBeDefined();
    retry.click();
    const request = http.expectOne((request) => request.url === '/api/items');
    expect(request.request.params.get('houseId')).toBe('1');
    fixture.componentInstance.reloadItems();
    http.expectNone((request) => request.url === '/api/items');
    request.flush([]);
    fixture.detectChanges();
    expect(fixture.componentInstance.itemError()).toBe('');
    expect(fixture.componentInstance.filter()).toBe('private');
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('does not display an invitation returned for a previously selected house', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    fixture.componentInstance.renewInvitation();
    const invitation = http.expectOne('/api/houses/1/invitation');
    fixture.componentInstance.houseControl.setValue(2);
    fixture.detectChanges();
    http
      .expectOne(
        (request) => request.url === '/api/locations' && request.params.get('houseId') === '2',
      )
      .flush([]);
    http.expectOne((request) => request.url === '/api/items').flush([]);
    invitation.flush({ code: 'first-house-invitation' });
    expect(fixture.componentInstance.invitationCode()).toBe('');
  });
});
