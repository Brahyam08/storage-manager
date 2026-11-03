import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocationManagerComponent } from './location-manager.component';
const locations = [
  { id: 1, houseId: 1, name: 'Despacho', path: 'Despacho', parentId: null, description: null },
  {
    id: 2,
    houseId: 1,
    name: 'Cajonera',
    path: 'Despacho → Cajonera',
    parentId: 1,
    description: null,
  },
  {
    id: 3,
    houseId: 1,
    name: 'Cajón',
    path: 'Despacho → Cajonera → Cajón',
    parentId: 2,
    description: null,
  },
  { id: 4, houseId: 1, name: 'Armario', path: 'Armario', parentId: null, description: null },
];
describe('Location management', () => {
  let fixture: ComponentFixture<LocationManagerComponent>;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LocationManagerComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LocationManagerComponent);
    fixture.componentRef.setInput('houseId', 1);
    fixture.detectChanges();
    http.expectOne('/api/locations?houseId=1').flush(locations);
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('excludes self and descendants as possible parents', () => {
    const component = fixture.componentInstance;
    component.edit(locations[0]);
    expect(component.parents().map((location) => location.id)).toEqual([4]);
    component.form.controls.parentId.setValue(3);
    component.save();
    http.expectNone('/api/locations/1');
  });

  it('saves current-house details and reloads paths after success', () => {
    const component = fixture.componentInstance;
    const changed = vi.fn();
    component.changed.subscribe(changed);
    component.edit(locations[0]);
    component.form.controls.name.setValue(' Estudio ');
    component.save();
    const request = http.expectOne('/api/locations/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({
      name: 'Estudio',
      description: '',
      parentId: null,
      houseId: 1,
    });
    request.flush({ ...locations[0], name: 'Estudio', path: 'Estudio' });
    http.expectOne('/api/locations?houseId=1').flush([
      { ...locations[0], name: 'Estudio', path: 'Estudio' },
      { ...locations[1], path: 'Estudio → Cajonera' },
    ]);
    expect(component.locations()[1].path).toBe('Estudio → Cajonera');
    expect(changed).toHaveBeenCalledTimes(1);
    expect(component.editing()).toBeNull();
  });

  it('requires confirmation and removes a location only on server success', () => {
    const component = fixture.componentInstance;
    component.confirmDelete();
    http.expectNone('/api/locations/4');
    component.askDelete(locations[3]);
    component.pendingDelete.set(null);
    component.confirmDelete();
    http.expectNone('/api/locations/4');
    component.askDelete(locations[3]);
    component.confirmDelete();
    component.confirmDelete();
    const request = http.expectOne('/api/locations/4');
    expect(request.request.method).toBe('DELETE');
    request.flush(null);
    http.expectOne('/api/locations?houseId=1').flush(locations.slice(0, 3));
    expect(component.locations()).toHaveLength(3);
    expect(component.pendingDelete()).toBeNull();
  });

  it('preserves a location when the server reports contents or history', () => {
    const component = fixture.componentInstance;
    component.askDelete(locations[0]);
    component.confirmDelete();
    http
      .expectOne('/api/locations/1')
      .flush(
        { error: 'La ubicación contiene otras ubicaciones, objetos o historial' },
        { status: 409, statusText: 'Conflict' },
      );
    expect(component.error()).toContain('historial');
    expect(component.locations()).toHaveLength(4);
    expect(component.pendingDelete()?.id).toBe(1);
  });

  it('clears edits and cancels pending updates when changing house', () => {
    const component = fixture.componentInstance;
    component.edit(locations[0]);
    component.save();
    const pending = http.expectOne('/api/locations/1');
    fixture.componentRef.setInput('houseId', 2);
    fixture.detectChanges();
    expect(pending.cancelled).toBe(true);
    expect(component.editing()).toBeNull();
    expect(component.locations()).toEqual([]);
    http.expectOne('/api/locations?houseId=2').flush([]);
  });
});
