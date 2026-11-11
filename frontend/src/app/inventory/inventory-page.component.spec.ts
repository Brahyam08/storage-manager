import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { InventoryPageComponent } from './inventory-page.component';
import { InventoryCsvExport } from './inventory-csv';
const csvExport = { download: vi.fn() };

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
        { provide: InventoryCsvExport, useValue: csvExport },
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user: signal({ id: 7, username: 'Alice', email: 'alice@example.test', role: 'VIEWER' }),
            clear: vi.fn(),
            storageWarning: signal(''),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(InventoryPageComponent);
    http.expectOne('/api/houses').flush(houses);
    fixture.detectChanges();
    http
      .expectOne('/api/locations?houseId=1&includeArchived=true')
      .flush([{ id: 3, houseId: 1, name: 'Cajón', path: 'Estudio → Cajón', parentId: null }]);
    http.expectOne('/api/locations?houseId=1').flush([]);
  });
  afterEach(() => {
    csvExport.download.mockReset();
    fixture.destroy();
    http.verify();
  });

  it('renames the owned house and updates its heading and selector without changing selection', async () => {
    (await vi.waitFor(() => http.expectOne((r) => r.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.managingHouses.set(true);
    component.beginHouseRename();
    component.renameForm.controls.name.setValue('  Mi nueva casa  ');
    component.renameHouse();
    component.renameHouse();
    const request = http.expectOne('/api/houses/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ name: 'Mi nueva casa' });
    request.flush({ ...houses[0], name: 'Mi nueva casa' });
    fixture.detectChanges();
    expect(component.selectedId()).toBe(1);
    expect(component.selectedHouse()?.name).toBe('Mi nueva casa');
    expect(fixture.nativeElement.querySelector('#house').textContent).toContain('Mi nueva casa');
    expect(fixture.nativeElement.querySelector('.current-house h2').textContent).toContain(
      'Mi nueva casa',
    );
    expect(component.renamingHouse()).toBeNull();
  });

  it('cancels renaming without sending a request and rejects blank names', async () => {
    (await vi.waitFor(() => http.expectOne((r) => r.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.beginHouseRename();
    component.renameForm.controls.name.setValue('   ');
    component.renameHouse();
    http.expectNone('/api/houses/1');
    expect(component.renameForm.controls.name.touched).toBe(true);
    component.cancelHouseRename();
    expect(component.selectedHouse()?.name).toBe('First');
    expect(component.renamingHouse()).toBeNull();
  });

  it('offers no rename action to members', async () => {
    (await vi.waitFor(() => http.expectOne((r) => r.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.houses.set([{ ...houses[0], ownerId: 99 }, houses[1]]);
    component.managingHouses.set(true);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('Cambiar nombre');
    component.beginHouseRename();
    expect(component.renamingHouse()).toBeNull();
    component.renameHouse();
    http.expectNone('/api/houses/1');
  });

  it('keeps a late rename response associated with its original house', async () => {
    (await vi.waitFor(() => http.expectOne((r) => r.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.beginHouseRename();
    component.renameForm.controls.name.setValue('Renamed first');
    component.renameHouse();
    const pending = http.expectOne('/api/houses/1');
    component.houseControl.setValue(2);
    http.expectOne('/api/locations?houseId=2&includeArchived=true').flush([]);
    http.expectOne((r) => r.url === '/api/items').flush([]);
    fixture.detectChanges();
    http.expectOne('/api/locations?houseId=2').flush([]);
    pending.flush({ ...houses[0], name: 'Renamed first' });
    expect(component.selectedHouse()?.name).toBe('Second');
    expect(component.renameNotice()).toBe('');
    expect(component.renamingHouse()).toBeNull();
    expect(component.houses()[0].name).toBe('Renamed first');
  });

  it('updates remote results without clearing the list', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([
      { id: 1, name: 'Antes', quantity: 1 },
    ]);
    const component = fixture.componentInstance;
    window.dispatchEvent(new Event('focus'));
    const pending = http.expectOne((request) => request.url === '/api/items');
    expect(component.items()[0].name).toBe('Antes');
    pending.flush([{ id: 1, name: 'Después', quantity: 2 }]);
    expect(component.items()[0].name).toBe('Después');
  });

  it('exports only the visible filtered objects and blocks stale search results', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([
      { id: 1, name: 'Compartido', visibility: 'HOUSE_SHARED', quantity: 3 },
      { id: 2, name: 'Privado', visibility: 'PRIVATE', quantity: 1 },
    ]);
    const component = fixture.componentInstance;
    component.filter.set('shared');
    component.exportCsv();
    expect(csvExport.download).toHaveBeenCalledExactlyOnceWith([
      { id: 1, name: 'Compartido', visibility: 'HOUSE_SHARED', quantity: 3 },
    ]);
    csvExport.download.mockClear();
    component.searchControl.setValue('nuevo');
    component.exportCsv();
    expect(csvExport.download).not.toHaveBeenCalled();
    const pending = await vi.waitFor(() =>
      http.expectOne((request) => request.url === '/api/items'),
    );
    component.exportCsv();
    expect(csvExport.download).not.toHaveBeenCalled();
    pending.flush([], { status: 503, statusText: 'Unavailable' });
    component.exportCsv();
    expect(csvExport.download).not.toHaveBeenCalled();
  });

  it('shows a download error without exposing data or breaking the inventory', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([
      { id: 1, name: 'Objeto', quantity: 1 },
    ]);
    csvExport.download.mockImplementationOnce(() => {
      throw new Error('browser unavailable');
    });
    fixture.componentInstance.exportCsv();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No se pudo preparar la descarga.');
    expect(fixture.componentInstance.items()).toHaveLength(1);
  });

  it('combines a direct location with search and archived items and keeps it in detail links', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.locationControl.setValue(3);
    const located = http.expectOne((request) => request.url === '/api/items');
    expect(located.request.params.get('locationId')).toBe('3');
    located.flush([
      {
        id: 1,
        name: 'Tornillos',
        kind: 'CONSUMABLE',
        quantity: 4,
        location: { id: 3, houseId: 1, path: 'Estudio → Cajón' },
      },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a.item-card-link').getAttribute('href')).toContain(
      'location=3',
    );
    component.searchControl.setValue('tor');
    const searched = await vi.waitFor(() =>
      http.expectOne((request) => request.url === '/api/items'),
    );
    expect(searched.request.params.get('locationId')).toBe('3');
    expect(searched.request.params.get('search')).toBe('tor');
    searched.flush([]);
    component.toggleArchived();
    const archived = http.expectOne((request) => request.url === '/api/items');
    expect(archived.request.params.get('locationId')).toBe('3');
    expect(archived.request.params.get('includeArchived')).toBe('true');
    archived.flush([]);
  });

  it('clears a location removed during inventory management and cancels the obsolete item load', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.locationControl.setValue(3);
    http.expectOne((request) => request.url === '/api/items').flush([]);
    component.locationsChanged();
    const stale = http.expectOne((request) => request.url === '/api/items');
    http.expectOne('/api/locations?houseId=1&includeArchived=true').flush([]);
    expect(component.locationControl.value).toBeNull();
    expect(stale.cancelled).toBe(true);
    const current = http.expectOne((request) => request.url === '/api/items');
    expect(current.request.params.has('locationId')).toBe(false);
    current.flush([]);
  });

  it('allows retrying location options without losing access to the inventory', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.locationControl.setValue(3);
    http.expectOne((request) => request.url === '/api/items').flush([]);
    component.reloadLocations();
    http
      .expectOne('/api/locations?houseId=1&includeArchived=true')
      .flush({}, { status: 503, statusText: 'Unavailable' });
    expect(component.locationControl.value).toBe(3);
    expect(component.locationOptions()).toHaveLength(1);
    http.expectNone((request) => request.url === '/api/items');
    expect(component.locationControl.disabled).toBe(true);
    expect(component.locationError()).not.toBe('');
    component.reloadLocations();
    http
      .expectOne('/api/locations?houseId=1&includeArchived=true')
      .flush([{ id: 3, houseId: 1, path: 'Cajón' }]);
    expect(component.locationControl.enabled).toBe(true);
    expect(component.locationError()).toBe('');
  });

  it('clears the location on a house change and cancels pending options and item requests', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.locationControl.setValue(3);
    const oldItems = http.expectOne((request) => request.url === '/api/items');
    component.reloadLocations();
    const oldOptions = http.expectOne('/api/locations?houseId=1&includeArchived=true');
    component.houseControl.setValue(2);
    fixture.detectChanges();
    expect(oldItems.cancelled).toBe(true);
    expect(oldOptions.cancelled).toBe(true);
    expect(component.locationControl.value).toBeNull();
    http.expectOne('/api/locations?houseId=2&includeArchived=true').flush([]);
    http.expectOne('/api/locations?houseId=2').flush([]);
    const current = http.expectOne((request) => request.url === '/api/items');
    expect(current.request.params.get('houseId')).toBe('2');
    expect(current.request.params.has('locationId')).toBe(false);
    current.flush([]);
  });

  it('can clear a location that contains no matching items', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    const component = fixture.componentInstance;
    component.locationControl.setValue(3);
    http.expectOne((request) => request.url === '/api/items').flush([]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('No encontramos coincidencias.');
    expect(fixture.nativeElement.textContent).toContain('Limpiar filtros');
    component.resetSearch();
    const request = http.expectOne((request) => request.url === '/api/items');
    expect(request.request.params.has('locationId')).toBe(false);
    request.flush([]);
  });

  it('keeps unassigned objects visible and offers a filter for them', async () => {
    const request = await vi.waitFor(() =>
      http.expectOne((request) => request.url === '/api/items'),
    );
    const item = {
      name: 'Teclado',
      kind: 'REUSABLE',
      quantity: 1,
      inUseQuantity: 0,
      availableQuantity: 1,
      ownerId: 7,
      visibility: 'PRIVATE',
      outOfStock: false,
      houseId: 1,
    };
    request.flush([
      { ...item, id: 1, location: null },
      { ...item, id: 2, location: { id: 3, houseId: 1, path: 'Cajón' } },
    ]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Sin ubicación');
    fixture.componentInstance.filter.set('unassigned');
    expect(fixture.componentInstance.visibleItems().map((item) => item.id)).toEqual([1]);
  });

  it('requests archived objects only after explicitly enabling them', async () => {
    (await vi.waitFor(() => http.expectOne((request) => request.url === '/api/items'))).flush([]);
    fixture.componentInstance.toggleArchived();
    const request = http.expectOne((request) => request.url === '/api/items');
    expect(request.request.params.get('includeArchived')).toBe('true');
    request.flush([]);
    fixture.componentInstance.toggleArchived();
    const active = http.expectOne((request) => request.url === '/api/items');
    expect(active.request.params.has('includeArchived')).toBe(false);
    active.flush([]);
  });

  it('orders all matches before paginating and exports the full ordered result', async () => {
    const request = await vi.waitFor(() => http.expectOne((r) => r.url === '/api/items'));
    const items = Array.from({ length: 26 }, (_, index) => ({
      id: index + 1,
      name: `Objeto ${index + 1}`,
      quantity: index + 1,
      kind: 'CONSUMABLE',
      visibility: 'PRIVATE',
      location: null,
    }));
    request.flush(items);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const select = host.querySelector<HTMLSelectElement>('#inventory-order')!;
    select.value = 'quantity-desc';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(host.querySelectorAll('.item-card-link')).toHaveLength(25);
    expect(host.querySelector('.item-card-link h3')?.textContent).toBe('Objeto 26');
    const pagination = host.querySelector('.inventory-pagination')!;
    expect(pagination.textContent).toContain('1–25 de 26');
    pagination.querySelectorAll('button')[1].click();
    fixture.detectChanges();

    expect(host.querySelectorAll('.item-card-link')).toHaveLength(1);
    expect(host.querySelector('.item-card-link h3')?.textContent).toBe('Objeto 1');
    expect(host.querySelector('.item-card-link')?.getAttribute('href')).toContain('page=2');
    expect(pagination.textContent).toContain('26–26 de 26');
    fixture.componentInstance.exportCsv();
    expect(csvExport.download).toHaveBeenCalledWith([...items].reverse());

    select.value = 'name';
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
    expect(host.querySelector('.item-card-link h3')?.textContent).toBe('Objeto 1');
    expect(host.querySelectorAll('.item-card-link')).toHaveLength(25);
  });

  it('distinguishes an individual state from a group quantity in the item cards', async () => {
    const request = await vi.waitFor(() =>
      http.expectOne((request) => request.url === '/api/items'),
    );
    const item = {
      kind: 'REUSABLE',
      name: 'Teclado',
      quantity: 1,
      availableQuantity: 1,
      inUseQuantity: 0,
      ownerId: 7,
      visibility: 'PRIVATE',
      outOfStock: false,
      location: { id: 3, houseId: 1, path: 'Cajón' },
    };
    request.flush([
      { ...item, id: 1, individual: true },
      { ...item, id: 2, individual: false },
    ]);
    fixture.detectChanges();
    const quantities = fixture.nativeElement.querySelectorAll('.item-quantity strong');
    expect(quantities[0].textContent.trim()).toBe('Disponible');
    expect(quantities[1].textContent.trim()).toBe('1');
  });

  it('cancels data from the previous house before loading the next', async () => {
    const first = await vi.waitFor(() =>
      http.expectOne(
        (request) => request.url === '/api/items' && request.params.get('houseId') === '1',
      ),
    );
    fixture.componentInstance.houseControl.setValue(2);
    fixture.detectChanges();
    http.expectOne('/api/locations?houseId=2&includeArchived=true').flush([]);
    http
      .expectOne(
        (request) =>
          request.url === '/api/locations' &&
          request.params.get('houseId') === '2' &&
          !request.params.has('includeArchived'),
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
    http.expectOne('/api/locations?houseId=2&includeArchived=true').flush([]);
    http
      .expectOne(
        (request) =>
          request.url === '/api/locations' &&
          request.params.get('houseId') === '2' &&
          !request.params.has('includeArchived'),
      )
      .flush([]);
    http.expectOne((request) => request.url === '/api/items').flush([]);
    invitation.flush({ code: 'first-house-invitation' });
    expect(fixture.componentInstance.invitationCode()).toBe('');
  });
});
