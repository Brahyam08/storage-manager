import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { signal } from '@angular/core';
import { AuthService } from '../core/auth.service';
import { ItemPageComponent } from './item-page.component';

const item = {
  id: 4,
  name: 'Tornillos',
  kind: 'CONSUMABLE',
  quantity: 10,
  ownerId: 7,
  inUseQuantity: 0,
  availableQuantity: 10,
  visibility: 'PRIVATE',
  outOfStock: false,
  description: null,
  category: null,
  location: {
    id: 3,
    houseId: 1,
    name: 'Cajón',
    path: 'Estudio → Cajón',
    parentId: null,
    description: null,
  },
};

describe('Item page navigation', () => {
  let fixture: ComponentFixture<ItemPageComponent>;
  let http: HttpTestingController;
  let params: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  beforeEach(() => {
    params = new BehaviorSubject(convertToParamMap({ id: '4' }));
    TestBed.configureTestingModule({
      imports: [ItemPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: AuthService, useValue: { user: signal({ id: 7 }) } },
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: params,
            snapshot: {
              queryParamMap: convertToParamMap({ house: '1', search: 'tor', filter: 'private' }),
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemPageComponent);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('loads a direct item URL and returns to the same inventory context', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Tornillos');
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.back();
    expect(router.navigate).toHaveBeenCalledWith(['/inventario'], {
      queryParams: { house: '1', search: 'tor', filter: 'private' },
    });
  });

  it('offers retry after a failed request without rendering another item', () => {
    http.expectOne('/api/items/4').flush({}, { status: 404, statusText: 'Not found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-item-detail')).toBeNull();
    fixture.nativeElement.querySelector('button').click();
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Tornillos');
  });

  it('cancels a previous item load when the route changes', () => {
    const first = http.expectOne('/api/items/4');
    params.next(convertToParamMap({ id: '5' }));
    expect(first.cancelled).toBe(true);
    http.expectOne('/api/items/5').flush({ ...item, id: 5, name: 'Arandelas' });
    expect(fixture.componentInstance.item()?.name).toBe('Arandelas');
  });

  it('does not query the API for an invalid item identifier', () => {
    http.expectOne('/api/items/4').flush(item);
    params.next(convertToParamMap({ id: 'not-an-id' }));
    http.expectNone('/api/items/NaN');
    expect(fixture.componentInstance.item()).toBeNull();
    expect(fixture.componentInstance.error()).toContain('No encontramos');
  });
});
