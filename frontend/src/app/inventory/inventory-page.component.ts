import { NgTemplateOutlet } from '@angular/common';
import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import {
  catchError,
  combineLatest,
  debounceTime,
  distinctUntilChanged,
  EMPTY,
  finalize,
  of,
  startWith,
  switchMap,
  tap,
} from 'rxjs';
import { WorkspaceLayoutComponent } from './workspace-layout.component';
import { InventoryCreateComponent } from './inventory-create.component';
import { Subject } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { House, InventoryItem, StorageLocation } from '../core/models';
import { InventoryCsvExport } from './inventory-csv';
import { onVisibleRefresh } from '../core/visible-refresh';

const PAGE_SIZE = 25;
const ITEM_ORDERS = [
  { value: 'registered', label: 'Orden de registro' },
  { value: 'name', label: 'Nombre (A–Z)' },
  { value: 'name-desc', label: 'Nombre (Z–A)' },
  { value: 'location', label: 'Ubicación (A–Z)' },
  { value: 'quantity-desc', label: 'Cantidad total (mayor primero)' },
  { value: 'quantity', label: 'Cantidad total (menor primero)' },
  { value: 'status', label: 'Estado (agotados primero)' },
  { value: 'visibility', label: 'Visibilidad (compartidos primero)' },
] as const;

type ItemOrder = (typeof ITEM_ORDERS)[number]['value'];
const inventoryCollator = new Intl.Collator('es', { numeric: true, sensitivity: 'base' });

function itemStatus(item: InventoryItem): number {
  if (item.archived) return 3;
  if (item.outOfStock) return 0;
  return item.inUseQuantity > 0 ? 1 : 2;
}

function compareItems(left: InventoryItem, right: InventoryItem, order: ItemOrder): number {
  switch (order) {
    case 'name-desc':
      return inventoryCollator.compare(right.name, left.name);
    case 'location':
      if (!left.location || !right.location) {
        return Number(!left.location) - Number(!right.location);
      }
      return inventoryCollator.compare(left.location.path, right.location.path);
    case 'quantity':
      return left.quantity - right.quantity;
    case 'quantity-desc':
      return right.quantity - left.quantity;
    case 'status':
      return itemStatus(left) - itemStatus(right);
    case 'visibility':
      return Number(left.visibility === 'PRIVATE') - Number(right.visibility === 'PRIVATE');
    default:
      return inventoryCollator.compare(left.name, right.name);
  }
}

type ItemFilter = 'unassigned' | 'all' | 'private' | 'shared' | 'empty' | 'consumable' | 'reusable';

@Component({
  selector: 'app-inventory-page',
  imports: [
    NgTemplateOutlet,
    ReactiveFormsModule,
    RouterLink,
    InventoryCreateComponent,
    WorkspaceLayoutComponent,
  ],
  templateUrl: './inventory-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryPageComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(InventoryApiService);
  private readonly csv = inject(InventoryCsvExport);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  private readonly refreshItems = new Subject<void>();
  private readonly refreshLocations = new Subject<void>();
  private currentHouseId: number | null = null;
  readonly locationOptions = signal<StorageLocation[]>([]);
  readonly locationError = signal('');
  readonly loadingLocations = signal(false);
  readonly locationControl = new FormControl<number | null>({
    value: this.initialLocation(),
    disabled: true,
  });
  readonly inventoryNotice = signal('');
  readonly houses = signal<House[]>([]);
  readonly items = signal<InventoryItem[]>([]);
  readonly loadingHouses = signal(true);
  readonly loadingItems = signal(false);
  readonly houseBusy = signal(false);
  readonly error = signal('');
  readonly itemError = signal('');
  readonly exportError = signal('');
  private readonly requestedSearch = signal<string | null>(null);
  readonly showArchived = signal(this.route.snapshot.queryParamMap.get('archived') === '1');
  readonly managingHouses = signal(this.route.snapshot.queryParamMap.get('view') === 'casas');
  readonly invitationCode = signal('');
  readonly notice = signal('');
  readonly filter = signal<ItemFilter>(this.initialFilter());
  readonly itemOrders = ITEM_ORDERS;
  readonly sortControl = new FormControl(
    this.parseOrder(this.route.snapshot.queryParamMap.get('sort')),
    { nonNullable: true },
  );
  readonly sort = toSignal(this.sortControl.valueChanges, { initialValue: this.sortControl.value });
  private readonly requestedPage = signal(this.positiveParam('page') ?? 1);
  private returnItemId = this.positiveParam('focus');
  private readonly itemList = viewChild<ElementRef<HTMLUListElement>>('itemList');
  readonly houseControl = new FormControl<number | null>({ value: null, disabled: true });
  readonly selectedId = toSignal(this.houseControl.valueChanges, { initialValue: null });
  readonly searchControl = new FormControl(this.route.snapshot.queryParamMap.get('search') ?? '', {
    nonNullable: true,
  });
  readonly selectedHouse = computed(
    () => this.houses().find((house) => house.id === this.selectedId()) ?? null,
  );
  readonly visibleItems = computed(() =>
    this.items().filter((item) => {
      switch (this.filter()) {
        case 'unassigned':
          return item.location === null;
        case 'private':
          return item.visibility === 'PRIVATE';
        case 'shared':
          return item.visibility === 'HOUSE_SHARED';
        case 'consumable':
          return item.kind === 'CONSUMABLE';
        case 'reusable':
          return item.kind === 'REUSABLE';
        case 'empty':
          return item.outOfStock;
        default:
          return true;
      }
    }),
  );
  readonly orderedItems = computed(() => {
    const order = this.sort();
    const items = this.visibleItems();
    if (order === 'registered') return items;
    return [...items].sort(
      (left, right) =>
        compareItems(left, right, order) ||
        inventoryCollator.compare(left.name, right.name) ||
        left.id - right.id,
    );
  });
  readonly pageCount = computed(() =>
    Math.max(1, Math.ceil(this.visibleItems().length / PAGE_SIZE)),
  );
  readonly page = computed(() => Math.min(this.requestedPage(), this.pageCount()));
  readonly pageStart = computed(() => (this.page() - 1) * PAGE_SIZE);
  readonly pageItems = computed(() =>
    this.orderedItems().slice(this.pageStart(), this.pageStart() + PAGE_SIZE),
  );
  readonly emptyCount = computed(
    () => this.visibleItems().filter((item) => item.outOfStock).length,
  );
  readonly createForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
  });
  readonly renamingHouse = signal<number | null>(null);
  readonly renameError = signal('');
  readonly renameNotice = signal('');
  readonly renameForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
  });
  readonly joinForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(128)]],
  });

  private checkingUpdates = false;

  constructor() {
    afterRenderEffect(() => {
      if (this.returnItemId === null) return;
      const list = this.itemList()?.nativeElement;
      if (!list) return;
      const item = list.querySelector<HTMLElement>(`#inventory-item-${this.returnItemId}`);
      this.returnItemId = null;
      this.reveal(item ?? list);
    });
    this.searchControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.resetPage());
    this.sortControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.resetPage());
    onVisibleRefresh(() => this.checkUpdates());
    combineLatest([
      this.houseControl.valueChanges.pipe(
        startWith(null),
        tap((houseId) => {
          if (houseId !== this.currentHouseId) {
            this.cancelHouseRename();
            this.renameNotice.set('');
            if (this.currentHouseId !== null) {
              this.resetPage();
              this.locationControl.setValue(null, { emitEvent: false });
            }
            this.currentHouseId = houseId;
            this.locationOptions.set([]);
            this.locationControl.disable({ emitEvent: false });
          }
          this.inventoryNotice.set('');
          this.exportError.set('');
          this.invitationCode.set('');
          this.notice.set('');
        }),
      ),
      this.searchControl.valueChanges.pipe(
        startWith(this.searchControl.value),
        debounceTime(250),
        distinctUntilChanged(),
      ),
      this.refreshItems.pipe(startWith(undefined)),
    ])
      .pipe(
        switchMap(([houseId, search]) => {
          this.requestedSearch.set(search.trim());
          this.items.set([]);
          this.itemError.set('');
          this.loadingItems.set(houseId !== null);
          if (houseId === null) return EMPTY;
          return this.api
            .items(houseId, search.trim(), this.showArchived(), this.locationControl.value)
            .pipe(
              catchError((error) => {
                this.itemError.set(apiError(error));
                return of([]);
              }),
            );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((items) => {
        if (!this.itemError()) this.applyItems(items);
        this.loadingItems.set(false);
      });
    this.locationControl.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.resetPage();
      this.refreshItems.next();
    });
    combineLatest([
      this.houseControl.valueChanges.pipe(startWith(null), distinctUntilChanged()),
      this.refreshLocations.pipe(startWith(undefined)),
    ])
      .pipe(
        switchMap(([houseId]) => {
          this.locationError.set('');
          this.locationControl.disable({ emitEvent: false });
          this.loadingLocations.set(houseId !== null);
          if (houseId === null) return of([]);
          return this.api.locations(houseId, true).pipe(
            catchError((error) => {
              this.locationError.set(apiError(error));
              return of([]);
            }),
            finalize(() => this.loadingLocations.set(false)),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((locations) => {
        if (this.selectedId() === null || this.locationError()) return;
        this.locationOptions.set(
          locations.filter(
            (location) => location.houseId === this.selectedId() && !location.deleted,
          ),
        );
        if (
          this.locationControl.value !== null &&
          !this.locationOptions().some((location) => location.id === this.locationControl.value)
        ) {
          this.locationControl.setValue(null);
        }
        if (!this.locationError()) this.locationControl.enable({ emitEvent: false });
      });
    const house = Number(this.route.snapshot.queryParamMap.get('house'));
    this.loadHouses(Number.isSafeInteger(house) && house > 0 ? house : undefined);
  }

  private initialFilter(): ItemFilter {
    const filter = this.route.snapshot.queryParamMap.get('filter');
    return ['unassigned', 'private', 'shared', 'empty', 'consumable', 'reusable'].includes(
      filter ?? '',
    )
      ? (filter as ItemFilter)
      : 'all';
  }

  private initialLocation(): number | null {
    return this.positiveParam('location');
  }

  private positiveParam(name: string): number | null {
    const id = Number(this.route.snapshot.queryParamMap.get(name));
    return Number.isSafeInteger(id) && id > 0 ? id : null;
  }

  private parseOrder(value: string | null): ItemOrder {
    return ITEM_ORDERS.find((order) => order.value === value)?.value ?? 'registered';
  }

  private applyItems(items: InventoryItem[]) {
    this.items.set(items);
    const index =
      this.returnItemId === null
        ? -1
        : this.orderedItems().findIndex((item) => item.id === this.returnItemId);
    this.requestedPage.set(index === -1 ? this.page() : Math.floor(index / PAGE_SIZE) + 1);
  }

  private resetPage() {
    this.returnItemId = null;
    this.requestedPage.set(1);
  }

  private reveal(element: HTMLElement) {
    element.focus({ preventScroll: true });
    element.scrollIntoView?.({ block: 'nearest' });
  }

  changePage(delta: number, heading: HTMLElement) {
    const next = this.page() + delta;
    if (next < 1 || next > this.pageCount()) return;
    this.requestedPage.set(next);
    this.reveal(heading);
  }

  reloadLocations() {
    if (!this.loadingLocations()) this.refreshLocations.next();
  }

  loadHouses(preferredId?: number) {
    this.loadingHouses.set(true);
    this.error.set('');
    this.houseControl.disable({ emitEvent: false });
    this.api
      .houses()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (houses) => {
          this.houses.set(houses);
          if (houses.length) this.houseControl.enable({ emitEvent: false });
          const id = preferredId ?? this.houseControl.value;
          this.houseControl.setValue(
            houses.some((house) => house.id === id) ? id! : (houses[0]?.id ?? null),
          );
          this.loadingHouses.set(false);
        },
        error: (error) => {
          this.error.set(apiError(error));
          this.loadingHouses.set(false);
          if (this.houses().length) this.houseControl.enable({ emitEvent: false });
        },
      });
  }

  beginHouseRename() {
    const house = this.selectedHouse();
    if (!house || house.ownerId !== this.auth.user()?.id || this.houseBusy()) return;
    this.renameForm.reset({ name: house.name });
    this.renameError.set('');
    this.renameNotice.set('');
    this.renamingHouse.set(house.id);
  }

  cancelHouseRename() {
    this.renamingHouse.set(null);
    this.renameError.set('');
    this.renameForm.reset();
  }

  renameHouse() {
    const house = this.selectedHouse();
    if (
      !house ||
      this.renamingHouse() !== house.id ||
      house.ownerId !== this.auth.user()?.id ||
      this.houseBusy()
    )
      return;
    if (this.renameForm.invalid) {
      this.renameForm.markAllAsTouched();
      return;
    }
    this.houseBusy.set(true);
    this.renameError.set('');
    this.api
      .renameHouse(house.id, this.renameForm.controls.name.value)
      .pipe(
        finalize(() => this.houseBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (updated) => {
          this.houses.update((houses) =>
            houses.map((current) => (current.id === house.id ? updated : current)),
          );
          if (this.selectedId() === house.id && this.renamingHouse() === house.id) {
            this.cancelHouseRename();
            this.renameNotice.set('Nombre de la casa actualizado.');
          }
        },
        error: (error) => {
          if (this.selectedId() === house.id && this.renamingHouse() === house.id)
            this.renameError.set(apiError(error));
        },
      });
  }

  createHouse() {
    if (this.createForm.invalid || this.houseBusy()) {
      this.createForm.markAllAsTouched();
      return;
    }
    this.houseBusy.set(true);
    this.error.set('');
    this.notice.set('');
    this.api
      .createHouse(this.createForm.controls.name.value)
      .pipe(
        finalize(() => this.houseBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (house) => {
          this.createForm.reset();
          this.managingHouses.set(false);
          this.loadHouses(house.id);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  joinHouse() {
    if (this.joinForm.invalid || this.houseBusy()) {
      this.joinForm.markAllAsTouched();
      return;
    }
    this.houseBusy.set(true);
    this.error.set('');
    this.notice.set('');
    this.api
      .joinHouse(this.joinForm.controls.code.value)
      .pipe(
        finalize(() => this.houseBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (house) => {
          this.joinForm.reset();
          this.managingHouses.set(false);
          this.loadHouses(house.id);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  renewInvitation() {
    const house = this.selectedHouse();
    if (!house || this.houseBusy()) return;
    this.houseBusy.set(true);
    this.error.set('');
    this.api
      .renewInvitation(house.id)
      .pipe(
        finalize(() => this.houseBusy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (result) => {
          if (this.selectedId() === house.id) {
            this.invitationCode.set(result.code);
            this.notice.set(
              'Comparte este código con quien quieras invitar. El código anterior ya no funciona.',
            );
          }
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  canExportCsv(): boolean {
    return (
      this.selectedId() !== null &&
      !this.loadingHouses() &&
      !this.loadingItems() &&
      !this.itemError() &&
      this.visibleItems().length > 0 &&
      this.requestedSearch() === this.searchControl.value.trim()
    );
  }

  exportCsv() {
    if (!this.canExportCsv()) return;
    this.exportError.set('');
    try {
      this.csv.download(this.orderedItems());
    } catch {
      this.exportError.set('No se pudo preparar la descarga. Inténtalo de nuevo.');
    }
  }

  private checkUpdates() {
    const houseId = this.selectedId();
    if (
      houseId === null ||
      this.loadingItems() ||
      this.checkingUpdates ||
      this.requestedSearch() !== this.searchControl.value.trim()
    )
      return;
    const previous = this.items();
    const search = this.searchControl.value.trim();
    const archived = this.showArchived();
    const location = this.locationControl.value;
    this.checkingUpdates = true;
    this.api
      .items(houseId, search, archived, location)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.checkingUpdates = false;
        }),
      )
      .subscribe({
        next: (items) => {
          if (
            this.selectedId() !== houseId ||
            this.items() !== previous ||
            this.loadingItems() ||
            this.searchControl.value.trim() !== search ||
            this.showArchived() !== archived ||
            this.locationControl.value !== location
          )
            return;
          this.itemError.set('');
          this.applyItems(items);
        },
        error: (error) => {
          if (this.selectedId() === houseId && this.items() === previous && error.status === 403) {
            this.items.set([]);
            this.itemError.set(apiError(error));
          }
        },
      });
  }

  reloadItems() {
    if (this.loadingItems() || this.selectedId() === null) return;
    this.inventoryNotice.set('');
    this.refreshItems.next();
  }

  locationsChanged() {
    this.inventoryNotice.set('');
    this.refreshLocations.next();
    this.refreshItems.next();
  }

  itemCreated() {
    this.inventoryNotice.set('');
    this.locationControl.setValue(null, { emitEvent: false });
    this.filter.set('all');
    this.searchControl.setValue('');
    this.refreshItems.next();
  }

  toggleArchived() {
    this.resetPage();
    this.showArchived.update((value) => !value);
    this.refreshItems.next();
  }

  setFilter(event: Event) {
    this.resetPage();
    this.filter.set((event.target as HTMLSelectElement).value as ItemFilter);
  }
  resetSearch() {
    if (this.locationControl.value !== null) this.locationControl.setValue(null);
    this.searchControl.setValue('');
    this.filter.set('all');
  }
  logout() {
    this.auth.clear();
    void this.router.navigate(['/acceso']);
  }
}
