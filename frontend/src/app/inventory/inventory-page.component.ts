import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
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
import { AuthService } from '../core/auth.service';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { House, InventoryItem } from '../core/models';

type ItemFilter = 'all' | 'private' | 'shared' | 'empty';

@Component({
  selector: 'app-inventory-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './inventory-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InventoryPageComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(InventoryApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly houses = signal<House[]>([]);
  readonly items = signal<InventoryItem[]>([]);
  readonly loadingHouses = signal(true);
  readonly loadingItems = signal(false);
  readonly houseBusy = signal(false);
  readonly error = signal('');
  readonly itemError = signal('');
  readonly managingHouses = signal(false);
  readonly invitationCode = signal('');
  readonly notice = signal('');
  readonly filter = signal<ItemFilter>('all');
  readonly houseControl = new FormControl<number | null>({ value: null, disabled: true });
  readonly selectedId = toSignal(this.houseControl.valueChanges, { initialValue: null });
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly selectedHouse = computed(
    () => this.houses().find((house) => house.id === this.selectedId()) ?? null,
  );
  readonly visibleItems = computed(() =>
    this.items().filter((item) => {
      switch (this.filter()) {
        case 'private':
          return item.visibility === 'PRIVATE';
        case 'shared':
          return item.visibility === 'HOUSE_SHARED';
        case 'empty':
          return item.outOfStock;
        default:
          return true;
      }
    }),
  );
  readonly units = computed(() =>
    this.visibleItems().reduce((total, item) => total + item.quantity, 0),
  );
  readonly emptyCount = computed(
    () => this.visibleItems().filter((item) => item.outOfStock).length,
  );
  readonly createForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
  });
  readonly joinForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(128)]],
  });

  constructor() {
    combineLatest([
      this.houseControl.valueChanges.pipe(
        startWith(null),
        tap(() => {
          this.invitationCode.set('');
          this.notice.set('');
        }),
      ),
      this.searchControl.valueChanges.pipe(
        startWith(''),
        debounceTime(250),
        distinctUntilChanged(),
      ),
    ])
      .pipe(
        switchMap(([houseId, search]) => {
          this.items.set([]);
          this.itemError.set('');
          this.loadingItems.set(houseId !== null);
          if (houseId === null) return EMPTY;
          return this.api.items(houseId, search.trim()).pipe(
            catchError((error) => {
              this.itemError.set(apiError(error));
              return of([]);
            }),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe((items) => {
        this.items.set(items);
        this.loadingItems.set(false);
      });
    this.loadHouses();
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

  setFilter(event: Event) {
    this.filter.set((event.target as HTMLSelectElement).value as ItemFilter);
  }
  resetSearch() {
    this.searchControl.setValue('');
    this.filter.set('all');
  }
  logout() {
    this.auth.clear();
    void this.router.navigate(['/acceso']);
  }
}
