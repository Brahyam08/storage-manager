import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Input,
  OnChanges,
  inject,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, finalize, takeUntil } from 'rxjs';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { LocationManagerComponent } from './location-manager.component';
import { reusableQuantity } from './item-kind.validator';
import { InventoryItem, StorageLocation, ItemKind } from '../core/models';

@Component({
  selector: 'app-inventory-create',
  imports: [ReactiveFormsModule, LocationManagerComponent],
  templateUrl: './inventory-create.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryCreateComponent implements OnChanges {
  @Input({ required: true }) houseId!: number;
  readonly locationsUpdated = output<void>();
  readonly created = output<void>();
  private readonly api = inject(InventoryApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly houseChanged = new Subject<void>();
  readonly locations = signal<StorageLocation[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly locationError = signal('');
  readonly notice = signal('');
  readonly mode = signal<'location' | 'item' | 'locations' | null>(null);
  private readonly nameValidators = [
    Validators.required,
    Validators.pattern(/\S/),
    Validators.maxLength(255),
  ];
  readonly locationForm = this.fb.group({
    name: this.fb.nonNullable.control('', this.nameValidators),
    description: this.fb.nonNullable.control('', Validators.maxLength(255)),
    parentId: this.fb.control<number | null>(null),
  });
  readonly itemForm = this.fb.group(
    {
      individual: this.fb.nonNullable.control(true),
      kind: this.fb.nonNullable.control<ItemKind>('CONSUMABLE', Validators.required),
      name: this.fb.nonNullable.control('', this.nameValidators),
      description: this.fb.nonNullable.control('', Validators.maxLength(255)),
      category: this.fb.nonNullable.control('', Validators.maxLength(255)),
      quantity: this.fb.nonNullable.control(1, [
        Validators.required,
        Validators.min(0),
        Validators.max(2147483647),
        Validators.pattern(/^\d+$/),
      ]),
      locationId: this.fb.control<number | null>(null, Validators.required),
      visibility: this.fb.nonNullable.control<InventoryItem['visibility']>(
        'PRIVATE',
        Validators.required,
      ),
    },
    { validators: reusableQuantity },
  );

  ngOnChanges() {
    this.houseChanged.next();
    this.mode.set(null);
    this.locations.set([]);
    this.error.set('');
    this.notice.set('');
    this.locationForm.reset();
    this.itemForm.reset();
    this.loadLocations();
  }

  loadLocations() {
    this.loading.set(true);
    this.locationError.set('');
    this.api
      .locations(this.houseId)
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (locations) => {
          this.locations.set(locations);
          const selected = this.itemForm.controls.locationId.value;
          if (selected !== null && !locations.some((location) => location.id === selected)) {
            this.itemForm.controls.locationId.setValue(null);
          }
          const parent = this.locationForm.controls.parentId.value;
          if (parent !== null && !locations.some((location) => location.id === parent)) {
            this.locationForm.controls.parentId.setValue(null);
          }
        },
        error: (error) => this.locationError.set(apiError(error)),
      });
  }

  open(mode: 'location' | 'item' | 'locations') {
    if (this.busy()) return;
    this.mode.set(mode);
    this.error.set('');
    this.notice.set('');
  }

  locationsChanged() {
    this.loadLocations();
    this.locationsUpdated.emit();
  }

  createLocation() {
    if (this.busy() || this.loading() || this.locationError()) return;
    if (this.locationForm.invalid) {
      this.locationForm.markAllAsTouched();
      return;
    }
    const value = this.locationForm.getRawValue();
    if (
      value.parentId !== null &&
      !this.locations().some((location) => location.id === value.parentId)
    )
      return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .createLocation({
        ...value,
        name: value.name.trim(),
        description: value.description.trim(),
        houseId: this.houseId,
      })
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (location) => {
          this.locations.update((locations) => [...locations, location]);
          this.locationForm.reset();
          this.itemForm.controls.locationId.setValue(location.id);
          this.notice.set(`Ubicación creada: ${location.path}. Ya puedes guardar objetos aquí.`);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  createItem() {
    if (this.busy() || this.loading() || this.locationError()) return;
    if (this.itemForm.invalid) {
      this.itemForm.markAllAsTouched();
      return;
    }
    const value = this.itemForm.getRawValue();
    if (
      value.locationId === null ||
      !this.locations().some((location) => location.id === value.locationId)
    )
      return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .createItem({
        ...value,
        individual: value.kind === 'REUSABLE' && value.individual,
        locationId: value.locationId,
        name: value.name.trim(),
        description: value.description.trim(),
        category: value.category.trim(),
      })
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.itemForm.reset();
          this.mode.set(null);
          this.notice.set('Objeto guardado. Hemos actualizado el inventario.');
          this.created.emit();
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }
}
