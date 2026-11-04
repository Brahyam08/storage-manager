import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, of, startWith, Subject, switchMap } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { InventoryItem } from '../core/models';
import { ItemDetailComponent } from './item-detail.component';

@Component({
  selector: 'app-item-page',
  imports: [RouterLink, ItemDetailComponent],
  template: `
    <main class="detail-page">
      <nav class="detail-page-nav" aria-label="Navegación del objeto">
        <a class="button secondary" routerLink="/inventario" [queryParams]="backParams"
          >Volver al inventario</a
        >
        <a class="button secondary" routerLink="/perfil">Mi perfil</a>
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
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemPageComponent {
  readonly auth = inject(AuthService);
  private readonly api = inject(InventoryApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly reload = new Subject<void>();
  readonly item = signal<InventoryItem | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  get backParams() {
    return {
      house: this.route.snapshot.queryParamMap.get('house') ?? this.item()?.location.houseId,
      search: this.route.snapshot.queryParamMap.get('search'),
      filter: this.route.snapshot.queryParamMap.get('filter'),
      ...(this.route.snapshot.queryParamMap.get('archived') === '1' ? { archived: '1' } : {}),
    };
  }

  constructor() {
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

  load() {
    if (!this.loading()) this.reload.next();
  }

  back() {
    void this.router.navigate(['/inventario'], { queryParams: this.backParams });
  }
}
