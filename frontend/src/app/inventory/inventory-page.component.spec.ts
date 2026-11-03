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
    expect(first.cancelled).toBe(true);
    http
      .expectOne((request) => request.url === '/api/items' && request.params.get('houseId') === '2')
      .flush([]);
    expect(fixture.componentInstance.selectedHouse()?.id).toBe(2);
    expect(fixture.componentInstance.items()).toEqual([]);
  });

  it('does not display an invitation returned for a previously selected house', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    fixture.componentInstance.renewInvitation();
    const invitation = http.expectOne('/api/houses/1/invitation');
    fixture.componentInstance.houseControl.setValue(2);
    http.expectOne((request) => request.url === '/api/items').flush([]);
    invitation.flush({ code: 'first-house-invitation' });
    expect(fixture.componentInstance.invitationCode()).toBe('');
  });
});
