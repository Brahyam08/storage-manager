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
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, Subject, finalize, switchMap, takeUntil, tap } from 'rxjs';
import { InventoryApiService } from '../core/inventory-api.service';
import { InventoryItem, ItemUse } from '../core/models';
import { apiError } from '../core/api-error';

@Component({
  selector: 'app-item-use-panel',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './item-use-panel.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemUsePanelComponent implements OnChanges {
  @Input({ required: true }) item!: InventoryItem;
  @Input({ required: true }) userId!: number;
  readonly updated = output<InventoryItem>();
  private readonly api = inject(InventoryApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly selectionChanged = new Subject<void>();
  readonly uses = signal<ItemUse[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly historyError = signal('');
  readonly committed = signal(false);
  readonly form = this.fb.nonNullable.group({ quantity: [1] });

  ngOnChanges() {
    this.selectionChanged.next();
    this.error.set('');
    this.committed.set(false);
    this.uses.set([]);
    this.form.controls.quantity.setValidators([
      Validators.required,
      Validators.min(1),
      Validators.max(this.item.availableQuantity),
      Validators.pattern(/^\d+$/),
    ]);
    this.form.reset({ quantity: 1 });
    this.load();
  }

  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.historyError.set('');
    this.api
      .uses(this.item.id)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (uses) => this.uses.set(uses),
        error: (error) => this.historyError.set(apiError(error)),
      });
  }

  take() {
    if (this.busy() || this.committed() || this.item.availableQuantity === 0) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.perform(this.api.takeItem(this.item.id, this.form.controls.quantity.value));
  }

  giveBack(use: ItemUse) {
    if (
      this.busy() ||
      this.committed() ||
      use.returnedAt ||
      (use.usedById !== this.userId && this.item.ownerId !== this.userId)
    )
      return;
    this.perform(this.api.returnItem(this.item.id, use.id));
  }

  private perform(action: Observable<ItemUse>) {
    this.busy.set(true);
    this.error.set('');
    action
      .pipe(
        tap(() => this.committed.set(true)),
        switchMap(() => this.api.item(this.item.id)),
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => this.updated.emit(item),
        error: (error) =>
          this.error.set(
            this.committed()
              ? 'La operación está registrada. Actualiza el objeto antes de repetirla.'
              : apiError(error),
          ),
      });
  }
}
