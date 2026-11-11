import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { signal } from '@angular/core';
import { AuthService } from '../core/auth.service';
import { ItemDetailComponent } from './item-detail.component';
import { By } from '@angular/platform-browser';
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
              queryParamMap: convertToParamMap({
                house: '1',
                search: 'tor',
                filter: 'private',
                location: '3',
              }),
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemPageComponent);
    http.expectOne('/api/houses').flush([
      { id: 1, name: 'Estudio', ownerId: 7 },
      { id: 2, name: 'Taller', ownerId: 7 },
    ]);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('keeps the draft when leaving or refreshing is cancelled', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    const detail = fixture.debugElement.query(By.directive(ItemDetailComponent))
      .componentInstance as ItemDetailComponent;
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);
    try {
      detail.open('edit');
      expect(page.confirmLeaving()).toBe(true);
      expect(confirm).not.toHaveBeenCalled();
      detail.editForm.controls.name.setValue('Mi borrador');
      detail.editForm.controls.name.markAsDirty();
      expect(page.confirmLeaving()).toBe(false);
      detail.refresh();
      http.expectNone('/api/items/4');
      expect(detail.editForm.controls.name.value).toBe('Mi borrador');
      confirm.mockReturnValue(true);
      expect(page.confirmLeaving()).toBe(true);
    } finally {
      confirm.mockRestore();
    }
  });

  it('checks remote changes on return without blanking the current view', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    window.dispatchEvent(new Event('focus'));
    const request = http.expectOne('/api/items/4');
    expect(fixture.componentInstance.item()).toEqual(item);
    expect(fixture.componentInstance.loading()).toBe(false);
    request.flush({ ...item, name: 'Cambio remoto' });
    expect(fixture.componentInstance.item()?.name).toBe('Cambio remoto');
  });

  it('preserves editing started during an automatic check', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    window.dispatchEvent(new Event('focus'));
    const request = http.expectOne('/api/items/4');
    const detail = fixture.debugElement.query(By.directive(ItemDetailComponent))
      .componentInstance as ItemDetailComponent;
    detail.open('edit');
    detail.editForm.controls.name.setValue('Mi borrador');
    request.flush({ ...item, name: 'Cambio remoto' });
    fixture.detectChanges();
    expect(detail.editForm.controls.name.value).toBe('Mi borrador');
    expect(fixture.componentInstance.item()?.name).toBe('Tornillos');
  });

  it('keeps current data on transient check failures', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    window.dispatchEvent(new Event('focus'));
    http.expectOne('/api/items/4').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(fixture.componentInstance.item()).toEqual(item);
  });

  it('does not check while editing', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    const detail = fixture.debugElement.query(By.directive(ItemDetailComponent))
      .componentInstance as ItemDetailComponent;
    detail.open('edit');
    window.dispatchEvent(new Event('focus'));
    http.expectNone('/api/items/4');
  });

  it('loads a direct item URL and returns to the same inventory context', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Tornillos');
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    fixture.componentInstance.back();
    expect(router.navigate).toHaveBeenCalledWith(['/inventario'], {
      queryParams: { house: '1', search: 'tor', filter: 'private', location: '3' },
    });
  });

  it('keeps the item house selected when leaving via the house selector is cancelled', () => {
    http.expectOne('/api/items/4').flush(item);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#house');
    expect(select.selectedOptions[0].textContent).toBe('Estudio');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(false);
    select.selectedIndex = Array.from(select.options).findIndex(
      (option) => option.textContent === 'Taller',
    );
    select.dispatchEvent(new Event('change'));
    expect(navigate).toHaveBeenCalledWith(['/inventario'], { queryParams: { house: 2 } });
    expect(select.selectedOptions[0].textContent).toBe('Estudio');
  });

  it('offers retry after a failed request without rendering another item', () => {
    http.expectOne('/api/items/4').flush({}, { status: 404, statusText: 'Not found' });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('app-item-detail')).toBeNull();
    fixture.nativeElement.querySelector('[role="alert"] button').click();
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
