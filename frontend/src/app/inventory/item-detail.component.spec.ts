import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ItemDetailComponent } from './item-detail.component';
import { InventoryItem } from '../core/models';

const item: InventoryItem = {
  kind: 'CONSUMABLE',
  id: 4,
  name: 'Tornillos',
  description: null,
  category: 'Ferretería',
  quantity: 10,
  ownerId: 7,
  visibility: 'HOUSE_SHARED',
  outOfStock: false,
  location: {
    id: 3,
    name: 'Cajón',
    path: 'Despacho → Cajón',
    houseId: 1,
    parentId: 2,
    description: null,
  },
};

describe('Item detail', () => {
  let fixture: ComponentFixture<ItemDetailComponent>;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ItemDetailComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemDetailComponent);
    fixture.componentRef.setInput('item', item);
    fixture.componentRef.setInput('userId', 7);
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('consumes five of ten once and uses the server remaining quantity', () => {
    const component = fixture.componentInstance;
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.open('consume');
    component.consumeForm.controls.quantity.setValue(5);
    component.consume();
    component.consume();
    const request = http.expectOne('/api/items/4/consume');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantity: 5 });
    request.flush({ ...item, quantity: 5 });
    expect(updated).toHaveBeenCalledExactlyOnceWith({ ...item, quantity: 5 });
  });

  it('does not send invalid consumption quantities', () => {
    const component = fixture.componentInstance;
    for (const quantity of [0, -1, 1.5, 11]) {
      component.consumeForm.controls.quantity.setValue(quantity);
      component.consume();
      expect(component.consumeForm.invalid).toBe(true);
    }
    http.expectNone('/api/items/4/consume');
  });

  it('preserves values on stock conflict and refreshes from the server', () => {
    const component = fixture.componentInstance;
    component.consumeForm.controls.quantity.setValue(5);
    component.consume();
    http
      .expectOne('/api/items/4/consume')
      .flush({ message: 'No hay suficientes unidades' }, { status: 409, statusText: 'Conflict' });
    expect(component.error()).toContain('No hay suficientes unidades');
    expect(component.consumeForm.controls.quantity.value).toBe(5);
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.refresh();
    http.expectOne('/api/items/4').flush({ ...item, quantity: 2 });
    expect(updated).toHaveBeenCalledWith({ ...item, quantity: 2 });
  });

  it('does not offer or submit editing for another member but permits consumption', () => {
    fixture.componentRef.setInput('userId', 8);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.open('edit');
    component.save();
    expect(component.mode()).toBeNull();
    http.expectNone('/api/items/4');
    expect(fixture.nativeElement.textContent).not.toContain('Editar objeto');
    component.open('consume');
    component.consume();
    http.expectOne('/api/items/4/consume').flush({ ...item, quantity: 9 });
  });

  it('saves owner details with the existing location instead of moving the item', () => {
    const component = fixture.componentInstance;
    component.editForm.patchValue({ name: ' Tornillos M3 ', visibility: 'PRIVATE' });
    component.save();
    const request = http.expectOne('/api/items/4');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      kind: 'CONSUMABLE',
      name: 'Tornillos M3',
      category: 'Ferretería',
      description: '',
      quantity: 10,
      locationId: 3,
      visibility: 'PRIVATE',
    });
    request.flush({ ...item, name: 'Tornillos M3', visibility: 'PRIVATE' });
  });

  it('cancels history and clears old data when selecting another item', () => {
    const component = fixture.componentInstance;
    component.loadHistory();
    const pending = http.expectOne('/api/items/4/consumptions');
    fixture.componentRef.setInput('item', {
      ...item,
      id: 5,
      name: 'Tuercas',
      quantity: 0,
      outOfStock: true,
    });
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.history()).toEqual([]);
    expect(component.mode()).toBeNull();
    component.open('consume');
    expect(component.mode()).toBeNull();
    component.consume();
    http.expectNone('/api/items/5/consume');
  });

  it('shows recorded quantities and remaining stock from consumption history', () => {
    const component = fixture.componentInstance;
    component.loadHistory();
    http.expectOne('/api/items/4/consumptions').flush([
      {
        id: 1,
        quantity: 5,
        remainingQuantity: 5,
        consumedById: 7,
        consumedAt: '2026-11-03T19:00:00',
        location: item.location,
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('5 unidades consumidas · Quedaron 5');
    expect(fixture.nativeElement.textContent).toContain('Despacho → Cajón');
  });
  it('hides consumption actions for reusable objects and never sends a consume request', () => {
    fixture.componentRef.setInput('item', { ...item, kind: 'REUSABLE', quantity: 1 });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.open('consume');
    component.consume();
    expect(component.mode()).toBeNull();
    http.expectNone('/api/items/4/consume');
    expect(fixture.nativeElement.textContent).not.toContain('Registrar consumo');
    expect(fixture.nativeElement.textContent).not.toContain('Ver consumos');
    expect(fixture.nativeElement.textContent).toContain('Reutilizable · 1 objeto guardado');
    component.editForm.controls.quantity.setValue(0);
    component.save();
    expect(component.editForm.hasError('reusableQuantity')).toBe(true);
    http.expectNone('/api/items/4');
  });
  it('moves the full quantity for a member and excludes current or foreign-house destinations', () => {
    fixture.componentRef.setInput('userId', 8);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openMove();
    const destination = { ...item.location, id: 8, name: 'Armario', path: 'Armario' };
    http
      .expectOne('/api/locations?houseId=1')
      .flush([item.location, destination, { ...destination, id: 9, houseId: 2 }]);
    expect(component.destinations()).toEqual([destination]);
    component.moveForm.controls.toLocationId.setValue(8);
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.move();
    component.move();
    const request = http.expectOne((request) => request.url === '/api/items/4/move');
    expect(request.request.method).toBe('PATCH');
    expect(request.request.params.get('toLocationId')).toBe('8');
    expect(request.request.params.get('quantity')).toBe('10');
    request.flush({ ...item, location: destination });
    expect(updated).toHaveBeenCalledExactlyOnceWith({ ...item, location: destination });
  });

  it('requires an allowed destination and never moves zero-stock items', () => {
    const component = fixture.componentInstance;
    component.openMove();
    http.expectOne('/api/locations?houseId=1').flush([]);
    component.move();
    expect(component.moveForm.touched).toBe(true);
    component.moveForm.controls.toLocationId.setValue(999);
    component.move();
    http.expectNone((request) => request.url.endsWith('/move'));
    fixture.componentRef.setInput('item', { ...item, quantity: 0, outOfStock: true });
    fixture.detectChanges();
    component.openMove();
    component.move();
    http.expectNone('/api/locations?houseId=1');
    expect(component.mode()).toBeNull();
  });

  it('cancels a pending movement when the selected item changes', () => {
    const component = fixture.componentInstance;
    component.openMove();
    http.expectOne('/api/locations?houseId=1').flush([{ ...item.location, id: 8 }]);
    component.moveForm.controls.toLocationId.setValue(8);
    component.move();
    const pending = http.expectOne((request) => request.url.endsWith('/move'));
    fixture.componentRef.setInput('item', { ...item, id: 5 });
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.destinations()).toEqual([]);
    expect(component.moveForm.controls.toLocationId.value).toBeNull();
    expect(component.busy()).toBe(false);
  });

  it('retains the destination and original location when the server rejects stale stock', () => {
    const component = fixture.componentInstance;
    component.openMove();
    http.expectOne('/api/locations?houseId=1').flush([{ ...item.location, id: 8 }]);
    component.moveForm.controls.toLocationId.setValue(8);
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.move();
    http
      .expectOne((request) => request.url.endsWith('/move'))
      .flush(
        { error: 'Se debe mover la cantidad completa del objeto' },
        { status: 400, statusText: 'Bad Request' },
      );
    expect(component.error()).toContain('cantidad completa');
    expect(component.moveForm.controls.toLocationId.value).toBe(8);
    expect(component.item.location.id).toBe(3);
    expect(updated).not.toHaveBeenCalled();
  });

  it('renders movement paths and discards a pending history after selection changes', () => {
    const component = fixture.componentInstance;
    component.loadMovements();
    http.expectOne('/api/items/4/history').flush([
      {
        id: 1,
        quantity: 10,
        movedAt: '2026-11-03T19:00:00',
        fromLocation: item.location,
        toLocation: { ...item.location, id: 8, path: 'Armario' },
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('De: Despacho → Cajón');
    expect(fixture.nativeElement.textContent).toContain('A: Armario');
    expect(fixture.nativeElement.textContent).toContain('10 unidades');
    component.loadMovements();
    const pending = http.expectOne('/api/items/4/history');
    fixture.componentRef.setInput('item', { ...item, id: 5 });
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.movementHistory()).toEqual([]);
  });
});
