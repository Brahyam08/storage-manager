import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InventoryCreateComponent } from './inventory-create.component';

const location = {
  id: 3,
  name: 'Cajón',
  path: 'Despacho → Cajón',
  houseId: 1,
  parentId: 2,
  description: null,
};

describe('Inventory creation', () => {
  let fixture: ComponentFixture<InventoryCreateComponent>;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [InventoryCreateComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventoryCreateComponent);
    fixture.componentRef.setInput('houseId', 1);
    fixture.detectChanges();
    http.expectOne('/api/locations?houseId=1').flush([location]);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('creates an explicitly individual reusable without a quantity input', () => {
    const component = fixture.componentInstance;
    component.open('item');
    component.itemForm.patchValue({
      name: 'Teclado',
      locationId: 3,
      kind: 'REUSABLE',
      individual: true,
      quantity: 1,
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#item-stock')).toBeNull();
    component.createItem();
    const request = http.expectOne('/api/items');
    expect(request.request.body).toMatchObject({ kind: 'REUSABLE', individual: true, quantity: 1 });
    request.flush({ id: 4 });
  });

  it('creates zero-stock items privately and submits only once while busy', () => {
    const component = fixture.componentInstance;
    component.itemForm.patchValue({ name: ' Tornillos ', locationId: 3, quantity: 0 });
    const created = vi.fn();
    component.created.subscribe(created);
    component.createItem();
    component.createItem();
    const request = http.expectOne('/api/items');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      individual: false,
      kind: 'CONSUMABLE',
      name: 'Tornillos',
      description: '',
      category: '',
      quantity: 0,
      locationId: 3,
      visibility: 'PRIVATE',
    });
    request.flush({ id: 4 });
    expect(created).toHaveBeenCalledTimes(1);
    expect(component.itemForm.controls.visibility.value).toBe('PRIVATE');
    expect(component.busy()).toBe(false);
  });

  it('rejects fractional, negative and overflowing quantities before sending', () => {
    const component = fixture.componentInstance;
    component.itemForm.patchValue({ name: 'Tornillos', locationId: 3 });
    for (const quantity of [-1, 1.5, 2147483648]) {
      component.itemForm.controls.quantity.setValue(quantity);
      component.createItem();
      expect(component.itemForm.invalid).toBe(true);
    }
    http.expectNone('/api/items');
  });

  it('creates nested locations in the selected house and selects the returned location', () => {
    const component = fixture.componentInstance;
    component.locationForm.patchValue({ name: ' Separador ', parentId: 3 });
    component.createLocation();
    const request = http.expectOne('/api/locations');
    expect(request.request.body).toEqual({
      name: 'Separador',
      description: '',
      parentId: 3,
      houseId: 1,
    });
    request.flush({ ...location, id: 4, parentId: 3, path: 'Despacho → Cajón → Separador' });
    expect(component.itemForm.controls.locationId.value).toBe(4);
    expect(component.locations()).toHaveLength(2);
  });

  it('clears house-specific inputs and cancels pending creation when the house changes', () => {
    const component = fixture.componentInstance;
    component.open('item');
    component.itemForm.patchValue({ name: 'Tornillos', locationId: 3 });
    component.createItem();
    const pending = http.expectOne('/api/items');
    fixture.componentRef.setInput('houseId', 2);
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.mode()).toBeNull();
    expect(component.itemForm.controls.locationId.value).toBeNull();
    expect(component.locations()).toEqual([]);
    expect(component.busy()).toBe(false);
    http.expectOne('/api/locations?houseId=2').flush([]);
  });

  it('keeps form values after an API failure and allows a deliberate retry', () => {
    const component = fixture.componentInstance;
    component.itemForm.patchValue({ name: 'Tornillos', locationId: 3 });
    component.createItem();
    http.expectOne('/api/items').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(component.itemForm.controls.name.value).toBe('Tornillos');
    expect(component.error()).toContain('permiso');
    expect(component.busy()).toBe(false);
    component.createItem();
    http.expectOne('/api/items').flush({ id: 4 });
  });
  it('requires at least one reusable object and sends its explicit kind', () => {
    const component = fixture.componentInstance;
    component.itemForm.patchValue({ name: 'Ratón', locationId: 3, kind: 'REUSABLE', quantity: 0 });
    component.createItem();
    expect(component.itemForm.hasError('reusableQuantity')).toBe(true);
    http.expectNone('/api/items');
    component.itemForm.controls.quantity.setValue(1);
    component.createItem();
    const request = http.expectOne('/api/items');
    expect(request.request.body.kind).toBe('REUSABLE');
    request.flush({ id: 4 });
    expect(component.itemForm.controls.kind.value).toBe('CONSUMABLE');
  });
});
