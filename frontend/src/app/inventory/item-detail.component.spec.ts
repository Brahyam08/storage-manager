import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ItemDetailComponent } from './item-detail.component';
import { InventoryItem } from '../core/models';

const item: InventoryItem = {
  inUseQuantity: 0,
  availableQuantity: 10,
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

  it('offers permanent deletion for an archived item with an explicit history warning', () => {
    fixture.componentRef.setInput('item', { ...item, archived: true });
    fixture.detectChanges();
    fixture.componentInstance.askDelete();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('todo su historial');
    expect(fixture.nativeElement.textContent).toContain('No podrás recuperarlos');
    fixture.componentInstance.deleteItem();
    const request = http.expectOne('/api/items/4');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('does not start deletion while pieces remain in use', () => {
    fixture.componentRef.setInput('item', { ...item, inUseQuantity: 1 });
    fixture.detectChanges();
    fixture.componentInstance.askDelete();
    fixture.componentInstance.deleteItem();
    expect(fixture.componentInstance.mode()).toBeNull();
    http.expectNone('/api/items/4');
  });

  it('archives only after confirmation and preserves the returned record', () => {
    const component = fixture.componentInstance;
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.askArchive();
    http.expectNone('/api/items/4/archive');
    component.setArchived(true);
    component.setArchived(true);
    const request = http.expectOne('/api/items/4/archive');
    expect(request.request.method).toBe('PATCH');
    request.flush({ ...item, archived: true });
    expect(updated).toHaveBeenCalledWith(expect.objectContaining({ archived: true, quantity: 10 }));
    expect(component.mode()).toBeNull();
  });

  it('offers restore and history instead of stock mutations for an archived item', () => {
    fixture.componentRef.setInput('item', { ...item, archived: true });
    fixture.detectChanges();
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.textContent).toContain('Restaurar objeto');
    expect(fixture.nativeElement.textContent).not.toContain('Registrar consumo');
    expect(fixture.nativeElement.textContent).not.toContain('Añadir unidades');
    component.open('edit');
    component.consume();
    component.openMove();
    expect(component.mode()).toBeNull();
    http.expectNone('/api/items/4/consume');
    component.setArchived(false);
    http.expectOne('/api/items/4/restore').flush({ ...item, archived: false });
  });

  it('keeps an archive conflict visible without clearing item data', () => {
    const component = fixture.componentInstance;
    component.askArchive();
    component.setArchived(true);
    http
      .expectOne('/api/items/4/archive')
      .flush({ error: 'Devuelve los objetos en uso' }, { status: 409, statusText: 'Conflict' });
    expect(component.error()).toContain('Devuelve los objetos');
    expect(component.item.quantity).toBe(10);
    expect(component.busy()).toBe(false);
  });

  it('does not allow another member to archive or restore the item', () => {
    fixture.componentRef.setInput('userId', 8);
    fixture.detectChanges();
    fixture.componentInstance.askArchive();
    fixture.componentInstance.setArchived(false);
    expect(fixture.componentInstance.mode()).toBeNull();
    http.expectNone('/api/items/4/restore');
  });

  it('shows availability instead of quantity for an individual reusable object', () => {
    fixture.componentRef.setInput('item', {
      ...item,
      kind: 'REUSABLE',
      individual: true,
      quantity: 1,
      availableQuantity: 1,
    });
    fixture.detectChanges();
    http.expectOne('/api/items/4/uses').flush([]);
    const component = fixture.componentInstance;
    expect(fixture.nativeElement.querySelector('.item-stock').textContent).toContain('Disponible');
    expect(fixture.nativeElement.textContent).not.toContain('Añadir unidades');
    component.open('restock');
    expect(component.mode()).toBeNull();
    component.restock();
    http.expectNone('/api/items/4/restocks');
    component.open('edit');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#edit-quantity')).toBeNull();
  });

  it('preserves quantity controls for a reusable group with only one object', () => {
    fixture.componentRef.setInput('item', {
      ...item,
      kind: 'REUSABLE',
      individual: false,
      quantity: 1,
      availableQuantity: 1,
    });
    fixture.detectChanges();
    http.expectOne('/api/items/4/uses').flush([]);
    expect(fixture.nativeElement.textContent).toContain('Añadir unidades');
    fixture.componentInstance.open('edit');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#edit-quantity')).not.toBeNull();
  });

  it('previews added units and submits only the increment once', () => {
    const component = fixture.componentInstance;
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.open('restock');
    component.restockForm.controls.quantity.setValue(5);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Tienes 10 + añades 5 → quedarán 15');
    component.restock();
    component.restock();
    const request = http.expectOne('/api/items/4/restocks');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantity: 5 });
    // Another session may have consumed units since the preview.
    request.flush({ ...item, quantity: 14, availableQuantity: 14 });
    expect(updated).toHaveBeenCalledWith(expect.objectContaining({ quantity: 14 }));
    expect(component.mode()).toBeNull();
    component.restock();
    http.expectNone('/api/items/4/restocks');
  });

  it('rejects invalid additions and overflowing previews without a request', () => {
    const component = fixture.componentInstance;
    component.open('restock');
    for (const quantity of [0, -1, 1.5, 2147483647]) {
      component.restockForm.controls.quantity.setValue(quantity);
      expect(component.restockTotal).toBeNull();
      component.restock();
    }
    http.expectNone('/api/items/4/restocks');
  });

  it('allows replenishing an exhausted consumable and stepping the amount', () => {
    fixture.componentRef.setInput('item', {
      ...item,
      quantity: 0,
      availableQuantity: 0,
      outOfStock: true,
    });
    fixture.detectChanges();
    const button = [...fixture.nativeElement.querySelectorAll('button')].find(
      (button) => (button as HTMLButtonElement).textContent?.trim() === 'Añadir unidades',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(false);
    button.click();
    const component = fixture.componentInstance;
    component.stepRestock(-1);
    expect(component.restockForm.controls.quantity.value).toBe(1);
    component.stepRestock(1);
    expect(component.restockTotal).toBe(2);
    component.restock();
    http.expectOne('/api/items/4/restocks').flush({ ...item, quantity: 2, availableQuantity: 2 });
  });

  it('does not offer or submit restocking for another owner', () => {
    fixture.componentRef.setInput('userId', 8);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.open('restock');
    expect(component.mode()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Añadir unidades');
    component.restock();
    http.expectNone('/api/items/4/restocks');
  });

  it('keeps the addition visible after a conflict and loads restock history separately', () => {
    const component = fixture.componentInstance;
    component.open('restock');
    component.restockForm.controls.quantity.setValue(5);
    component.restock();
    http
      .expectOne('/api/items/4/restocks')
      .flush({ error: 'Añade menos unidades' }, { status: 409, statusText: 'Conflict' });
    expect(component.restockForm.controls.quantity.value).toBe(5);
    expect(component.error()).toContain('Añade menos unidades');
    expect(component.busy()).toBe(false);
    component.loadRestocks();
    http.expectOne('/api/items/4/restocks').flush([
      {
        id: 1,
        quantity: 5,
        resultingQuantity: 15,
        restockedById: 7,
        restockedAt: '2026-11-04T12:00:00Z',
        location: item.location,
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Historial de reposiciones');
    expect(fixture.nativeElement.textContent).toContain('Total tras reponer: 15');
  });

  it('cancels restock history when selecting a different item', () => {
    const component = fixture.componentInstance;
    component.loadRestocks();
    const oldRequest = http.expectOne('/api/items/4/restocks');
    fixture.componentRef.setInput('item', { ...item, id: 5 });
    fixture.detectChanges();
    expect(oldRequest.cancelled).toBe(true);
    expect(component.restocks()).toEqual([]);
    expect(component.mode()).toBeNull();
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
      individual: false,
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
    fixture.componentRef.setInput('item', {
      ...item,
      kind: 'REUSABLE',
      quantity: 1,
      availableQuantity: 1,
    });
    fixture.detectChanges();
    http.expectOne('/api/items/4/uses').flush([]);
    const component = fixture.componentInstance;
    component.open('consume');
    component.consume();
    expect(component.mode()).toBeNull();
    http.expectNone('/api/items/4/consume');
    expect(fixture.nativeElement.textContent).not.toContain('Registrar consumo');
    expect(fixture.nativeElement.textContent).not.toContain('Ver consumos');
    expect(fixture.nativeElement.textContent).toContain('Reutilizable');
    const stock = fixture.nativeElement.querySelector('[aria-label="Existencias del objeto"]');
    expect(stock.querySelector('strong').textContent).toBe('1');
    expect(stock.textContent).toContain('objeto registrado');
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

  it('renders an unassigned item and can choose a new location in its house', () => {
    fixture.componentRef.setInput('item', { ...item, location: null, houseId: 1 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sin ubicación');
    const component = fixture.componentInstance;
    component.openMove();
    http.expectOne('/api/locations?houseId=1').flush([{ ...item.location, id: 8 }]);
    component.moveForm.controls.toLocationId.setValue(8);
    component.move();
    http
      .expectOne((request) => request.url === '/api/items/4/move')
      .flush({ ...item, location: { ...item.location, id: 8 } });
  });

  it('requires an allowed destination and allows relocating zero-stock records', () => {
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
    http.expectOne('/api/locations?houseId=1').flush([{ ...item.location, id: 8 }]);
    component.moveForm.controls.toLocationId.setValue(8);
    component.move();
    const request = http.expectOne((request) => request.url === '/api/items/4/move');
    expect(request.request.params.get('quantity')).toBe('0');
    request.flush({ ...item, quantity: 0, location: { ...item.location, id: 8 } });
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
    expect(component.item.location?.id).toBe(3);
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
  it('renders stored HTML payloads as text instead of executable elements', () => {
    const html = '<img src=x onerror="alert(1)">';
    const script = '<script>alert(2)</script>';
    fixture.componentRef.setInput('item', {
      ...item,
      name: html,
      description: script,
      location: { ...item.location, path: html },
    });
    fixture.detectChanges();
    const element: HTMLElement = fixture.nativeElement;
    expect(element.textContent).toContain(html);
    expect(element.textContent).toContain(script);
    expect(element.querySelector('img, script, [onerror]')).toBeNull();
    fixture.componentInstance.loadMovements();
    http.expectOne('/api/items/4/history').flush([
      {
        id: 1,
        quantity: 1,
        movedAt: '2026-11-03T19:00:00',
        fromLocation: { ...item.location, path: html },
        toLocation: { ...item.location, id: 8, path: script },
      },
    ]);
    fixture.detectChanges();
    expect(element.querySelector('img, script, [onerror]')).toBeNull();
    expect(element.textContent).toContain('A: ' + script);
  });
  it('requires owner confirmation before deletion and emits only after success', () => {
    const component = fixture.componentInstance;
    const deleted = vi.fn();
    component.deleted.subscribe(deleted);
    component.deleteItem();
    http.expectNone('/api/items/4');
    component.askDelete();
    component.mode.set(null);
    component.deleteItem();
    http.expectNone('/api/items/4');
    component.askDelete();
    component.deleteItem();
    component.deleteItem();
    const request = http.expectOne('/api/items/4');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(deleted).toHaveBeenCalledExactlyOnceWith(4);
  });

  it('keeps an item visible when deletion is refused because it is in use', () => {
    const component = fixture.componentInstance;
    const deleted = vi.fn();
    component.deleted.subscribe(deleted);
    component.askDelete();
    component.deleteItem();
    http
      .expectOne('/api/items/4')
      .flush({ error: 'El objeto está en uso' }, { status: 409, statusText: 'Conflict' });
    expect(component.error()).toContain('en uso');
    expect(component.item.id).toBe(4);
    expect(deleted).not.toHaveBeenCalled();
    expect(component.busy()).toBe(false);
  });

  it('does not offer or submit deletion for another owner', () => {
    fixture.componentRef.setInput('userId', 8);
    fixture.detectChanges();
    fixture.componentInstance.askDelete();
    fixture.componentInstance.deleteItem();
    http.expectNone('/api/items/4');
    expect(fixture.nativeElement.textContent).not.toContain('Eliminar objeto');
  });

  it('cancels a pending delete on selection changes', () => {
    const component = fixture.componentInstance;
    component.askDelete();
    component.deleteItem();
    const pending = http.expectOne('/api/items/4');
    fixture.componentRef.setInput('item', { ...item, id: 5 });
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.mode()).toBeNull();
    expect(component.busy()).toBe(false);
  });
});
