import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ItemUsePanelComponent } from './item-use-panel.component';
import { InventoryItem, ItemUse } from '../core/models';

const item: InventoryItem = {
  id: 4,
  name: 'Ratón',
  description: null,
  category: null,
  quantity: 1,
  inUseQuantity: 0,
  availableQuantity: 1,
  kind: 'REUSABLE',
  ownerId: 7,
  visibility: 'HOUSE_SHARED',
  outOfStock: false,
  location: { id: 3, name: 'Cajón', path: 'Cajón', description: null, houseId: 1, parentId: null },
};
const use: ItemUse = {
  id: 9,
  quantity: 1,
  usedById: 8,
  takenAt: '2026-11-03T19:00:00',
  returnedAt: null,
  location: item.location,
};

describe('Reusable item usage', () => {
  let fixture: ComponentFixture<ItemUsePanelComponent>;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ItemUsePanelComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ItemUsePanelComponent);
    fixture.componentRef.setInput('item', item);
    fixture.componentRef.setInput('userId', 8);
    fixture.detectChanges();
    http.expectOne('/api/items/4/uses').flush([]);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('takes once and refreshes authoritative availability without reducing total', () => {
    const component = fixture.componentInstance;
    const updated = vi.fn();
    component.updated.subscribe(updated);
    component.take();
    component.take();
    const request = http.expectOne('/api/items/4/uses');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantity: 1 });
    request.flush(use);
    const taken = { ...item, availableQuantity: 0, inUseQuantity: 1 };
    http.expectOne('/api/items/4').flush(taken);
    expect(updated).toHaveBeenCalledExactlyOnceWith(taken);
    expect(taken.quantity).toBe(1);
  });

  it('rejects invalid quantities before requesting', () => {
    const component = fixture.componentInstance;
    for (const quantity of [0, -1, 0.5, 2]) {
      component.form.controls.quantity.setValue(quantity);
      component.take();
    }
    http.expectNone('/api/items/4/uses');
    expect(component.form.invalid).toBe(true);
  });

  it('does not offer returning another member usage and permits returning own usage', () => {
    const component = fixture.componentInstance;
    component.giveBack({ ...use, usedById: 10 });
    http.expectNone('/api/items/4/uses/9/return');
    component.giveBack(use);
    http
      .expectOne('/api/items/4/uses/9/return')
      .flush({ ...use, returnedAt: '2026-11-03T20:00:00' });
    http.expectOne('/api/items/4').flush(item);
  });

  it('prevents repeating an accepted action if refreshing the item fails', () => {
    const component = fixture.componentInstance;
    component.take();
    http.expectOne('/api/items/4/uses').flush(use);
    http.expectOne('/api/items/4').error(new ProgressEvent('offline'));
    expect(component.committed()).toBe(true);
    expect(component.error()).toContain('operación está registrada');
    component.take();
    http.expectNone('/api/items/4/uses');
  });

  it('cancels pending operations and clears history when selecting another item', () => {
    const component = fixture.componentInstance;
    component.take();
    const pending = http.expectOne('/api/items/4/uses');
    fixture.componentRef.setInput('item', { ...item, id: 5 });
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.uses()).toEqual([]);
    expect(component.busy()).toBe(false);
    http.expectOne('/api/items/5/uses').flush([]);
  });
});
