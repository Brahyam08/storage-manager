import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { FormControl } from '@angular/forms';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { catchError, finalize, of, startWith, Subject, switchMap } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { InventoryItem } from '../core/models';
import { ItemDetailComponent } from './item-detail.component';
import { WorkspaceLayoutComponent } from './workspace-layout.component';
import { onVisibleRefresh } from '../core/visible-refresh';

@Component({
  selector: 'app-item-page',
  imports: [RouterLink, ItemDetailComponent, WorkspaceLayoutComponent],
  host: { '(window:beforeunload)': 'warnBeforeUnload($event)' },
  template: `
    <app-workspace-layout
      [houses]="houses()"
      [houseControl]="houseControl"
      housePlaceholder="Selecciona una casa"
      (showInventory)="back()"
      (showHouses)="openHouses()"
      (signOut)="logout()"
    >
      <nav class="detail-page-nav" aria-label="Navegación del objeto">
        <a class="button secondary" routerLink="/inventario" [queryParams]="backParams"
          >Volver al inventario</a
        >
      </nav>
      @if (loading()) {
        <p role="status">Cargando objeto…</p>
      } @else if (error()) {
        <div class="error-banner" role="alert">
          {{ error() }}
          <button class="text-button" type="button" (click)="load()">Reintentar</button>
        </div>
      } @else if (item(); as current) {
        <app-item-detail
          [item]="current"
          [userId]="auth.user()!.id"
          (updated)="item.set($event)"
          (deleted)="back()"
        />
      }
    </app-workspace-layout>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemPageComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  @ViewChild(ItemDetailComponent) private detail?: ItemDetailComponent;
  private readonly destroyRef = inject(DestroyRef);
  private checkingUpdates = false;
  private readonly reload = new Subject<void>();
  readonly item = signal<InventoryItem | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly houses = toSignal(this.api.houses().pipe(catchError(() => of([]))), {
    initialValue: [],
  });
  readonly houseControl = new FormControl<number | null>({ value: null, disabled: true });
  private readonly currentHouseId = computed(() => {
    const item = this.item();
    return item?.houseId ?? item?.location?.houseId ?? Number(this.backParams.house);
  });
  private readonly selectedHouseId = computed(
    () => this.houses().find((house) => house.id === this.currentHouseId())?.id ?? null,
  );
  get backParams() {
    const { queryParamMap, paramMap } = this.route.snapshot;
    return {
      house: queryParamMap.get('house') ?? this.item()?.houseId ?? this.item()?.location?.houseId,
      search: queryParamMap.get('search'),
      filter: queryParamMap.get('filter'),
      ...(queryParamMap.get('location') ? { location: queryParamMap.get('location') } : {}),
      ...(queryParamMap.get('archived') === '1' ? { archived: '1' } : {}),
      ...(queryParamMap.has('sort') ? { sort: queryParamMap.get('sort') } : {}),
      ...(queryParamMap.has('page')
        ? { page: queryParamMap.get('page'), focus: paramMap.get('id') }
        : {}),
    };
  }

  constructor() {
    effect(() => {
      this.houseControl.setValue(this.selectedHouseId(), { emitEvent: false });
      if (this.houses().length) this.houseControl.enable({ emitEvent: false });
    });
    this.houseControl.valueChanges.pipe(takeUntilDestroyed()).subscribe((house) => {
      // Keep showing the item's house if navigation is cancelled by the draft guard.
      this.houseControl.setValue(this.selectedHouseId(), { emitEvent: false });
      if (house !== null && house !== this.selectedHouseId()) {
        void this.router.navigate(['/inventario'], { queryParams: { house } });
      }
    });
    onVisibleRefresh(() => this.checkUpdates());
    this.route.paramMap
      .pipe(
        switchMap((params) =>
          this.reload.pipe(
            startWith(undefined),
            switchMap(() => this.fetch(params.get('id'))),
          ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((item) => {
        this.item.set(item);
        this.loading.set(false);
      });
  }

  private fetch(id: string | null) {
    this.loading.set(true);
    this.item.set(null);
    this.error.set('');
    if (!id || !/^[1-9]\d*$/.test(id) || !Number.isSafeInteger(Number(id))) {
      this.error.set('No encontramos ese objeto.');
      return of(null);
    }
    return this.api.item(Number(id)).pipe(
      catchError((error) => {
        this.error.set(apiError(error));
        return of(null);
      }),
    );
  }

  private checkUpdates() {
    const previous = this.item();
    const detail = this.detail;
    if (!previous || !detail || !detail.canRefresh() || this.loading() || this.checkingUpdates)
      return;
    this.checkingUpdates = true;
    this.api
      .item(previous.id)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.checkingUpdates = false;
        }),
      )
      .subscribe({
        next: (item) => {
          if (
            this.item() !== previous ||
            this.detail !== detail ||
            !detail.canRefresh() ||
            this.loading()
          )
            return;
          if (JSON.stringify(item) !== JSON.stringify(previous)) this.item.set(item);
        },
        error: (error) => {
          if (this.item() === previous && [403, 404].includes(error.status)) {
            this.item.set(null);
            this.error.set(apiError(error));
          }
        },
      });
  }

  load() {
    if (!this.loading()) this.reload.next();
  }

  confirmLeaving(): boolean {
    return !this.auth.user() || (this.detail?.confirmDiscardEdit() ?? true);
  }

  warnBeforeUnload(event: BeforeUnloadEvent) {
    if (this.detail?.editForm.dirty) event.preventDefault();
  }

  openHouses() {
    void this.router.navigate(['/inventario'], {
      queryParams: { house: this.selectedHouseId() ?? this.backParams.house, view: 'casas' },
    });
  }

  logout() {
    if (!this.confirmLeaving()) return;
    this.auth.clear();
    void this.router.navigate(['/acceso']);
  }

  back() {
    void this.router.navigate(['/inventario'], { queryParams: this.backParams });
  }
}
