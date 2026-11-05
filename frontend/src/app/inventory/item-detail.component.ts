import {
  AfterViewInit,
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Input,
  OnChanges,
  SimpleChanges,
  inject,
  output,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, finalize, takeUntil } from 'rxjs';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { ItemUsePanelComponent } from './item-use-panel.component';
import { reusableQuantity } from './item-kind.validator';
import {
  InventoryItem,
  ItemConsumption,
  ItemRestock,
  ItemKind,
  StorageLocation,
  ItemMovement,
} from '../core/models';

@Component({
  selector: 'app-item-detail',
  imports: [ReactiveFormsModule, DatePipe, ItemUsePanelComponent],
  templateUrl: './item-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemDetailComponent implements OnChanges, AfterViewInit {
  @Input({ required: true }) item!: InventoryItem;
  @Input({ required: true }) userId!: number;
  @ViewChild('detailTitle') private title?: ElementRef<HTMLHeadingElement>;
  @ViewChild('actionTitle') set actionTitle(title: ElementRef<HTMLHeadingElement> | undefined) {
    title?.nativeElement.focus();
  }
  @ViewChild('editTitle') set editTitle(title: ElementRef<HTMLHeadingElement> | undefined) {
    if (!title) return;
    const element = title.nativeElement;
    const viewport = element.ownerDocument.defaultView;
    if (viewport?.matchMedia?.('(max-width: 760px)').matches) {
      element.focus({ preventScroll: true });
      element.scrollIntoView({ block: 'start', behavior: 'instant' });
    }
  }
  @ViewChild(ItemUsePanelComponent) private usePanel?: ItemUsePanelComponent;

  canRefresh() {
    return !this.mode() && !this.busy() && !this.usePanel?.busy() && !this.usePanel?.form.dirty;
  }

  readonly updated = output<InventoryItem>();
  readonly deleted = output<number>();
  private readonly api = inject(InventoryApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly selectionChanged = new Subject<void>();
  readonly busy = signal(false);
  readonly error = signal('');
  readonly mode = signal<
    | 'edit'
    | 'consume'
    | 'restock'
    | 'restocks'
    | 'history'
    | 'move'
    | 'movements'
    | 'delete'
    | 'archive'
    | null
  >(null);
  readonly history = signal<ItemConsumption[]>([]);
  readonly restocks = signal<ItemRestock[]>([]);
  readonly restocksLoaded = signal(false);
  readonly restockForm = this.fb.nonNullable.group({ quantity: [1] });
  readonly destinations = signal<StorageLocation[]>([]);
  readonly movementHistory = signal<ItemMovement[]>([]);
  readonly movementsLoaded = signal(false);
  readonly moveForm = this.fb.group({
    toLocationId: this.fb.control<number | null>(null, Validators.required),
  });
  readonly historyLoaded = signal(false);
  readonly editForm = this.fb.nonNullable.group(
    {
      individual: this.fb.nonNullable.control(false),
      kind: this.fb.nonNullable.control<ItemKind>('CONSUMABLE', Validators.required),
      name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]],
      description: ['', Validators.maxLength(255)],
      category: ['', Validators.maxLength(255)],
      quantity: [
        0,
        [
          Validators.required,
          Validators.min(0),
          Validators.max(2147483647),
          Validators.pattern(/^\d+$/),
        ],
      ],
      visibility: this.fb.nonNullable.control<InventoryItem['visibility']>(
        'PRIVATE',
        Validators.required,
      ),
    },
    { validators: reusableQuantity },
  );
  readonly consumeForm = this.fb.nonNullable.group({ quantity: [1] });

  ngOnChanges(changes: SimpleChanges) {
    this.selectionChanged.next();
    this.mode.set(null);
    this.error.set('');
    this.history.set([]);
    this.restocks.set([]);
    this.restocksLoaded.set(false);
    this.destinations.set([]);
    this.movementHistory.set([]);
    this.movementsLoaded.set(false);
    this.moveForm.reset();
    this.historyLoaded.set(false);
    this.editForm.reset({
      individual: this.item.individual ?? false,
      kind: this.item.kind,
      name: this.item.name,
      description: this.item.description ?? '',
      category: this.item.category ?? '',
      quantity: this.item.quantity,
      visibility: this.item.visibility,
    });
    this.consumeForm.controls.quantity.setValidators([
      Validators.required,
      Validators.min(1),
      Validators.max(this.item.quantity),
      Validators.pattern(/^\d+$/),
    ]);
    this.consumeForm.reset({ quantity: 1 });
    this.restockForm.controls.quantity.setValidators([
      Validators.required,
      Validators.min(1),
      Validators.max(2147483647 - this.item.quantity),
      Validators.pattern(/^\d+$/),
    ]);
    this.restockForm.reset({ quantity: 1 });
    if (changes['item']?.previousValue?.id !== this.item.id) this.title?.nativeElement.focus();
  }

  ngAfterViewInit() {
    this.title?.nativeElement.focus();
  }

  open(mode: 'edit' | 'consume' | 'restock') {
    if (this.item.archived) return;
    if (
      this.busy() ||
      ((mode === 'edit' || mode === 'restock') && this.item.ownerId !== this.userId) ||
      (mode === 'restock' && this.item.individual) ||
      (mode === 'consume' && (this.item.kind === 'REUSABLE' || this.item.quantity === 0))
    )
      return;
    this.mode.set(mode);
    this.error.set('');
  }

  save() {
    if (this.item.archived) return;
    if (this.busy() || this.item.ownerId !== this.userId) return;
    if (this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const value = this.editForm.getRawValue();
    this.busy.set(true);
    this.error.set('');
    this.api
      .updateItem(this.item.id, {
        ...value,
        individual: value.kind === 'REUSABLE' && value.individual,
        name: value.name.trim(),
        description: value.description.trim(),
        category: value.category.trim(),
        locationId: this.item.location?.id ?? null,
      })
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => this.updated.emit(item),
        error: (error) => this.error.set(apiError(error)),
      });
  }

  consume() {
    if (this.item.archived) return;
    if (this.busy() || this.item.kind === 'REUSABLE') return;
    if (this.consumeForm.invalid) {
      this.consumeForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api
      .consumeItem(this.item.id, this.consumeForm.controls.quantity.value)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => this.updated.emit(item),
        error: (error) => this.error.set(apiError(error)),
      });
  }

  get restockTotal(): number | null {
    return this.restockForm.valid
      ? this.item.quantity + this.restockForm.controls.quantity.value
      : null;
  }

  stepRestock(delta: number) {
    if (this.busy()) return;
    const current = this.restockForm.controls.quantity.value;
    const next = Number.isInteger(current) ? current + delta : 1;
    if (next >= 1 && next <= 2147483647 - this.item.quantity) {
      this.restockForm.controls.quantity.setValue(next);
    }
  }

  restock() {
    if (this.item.archived) return;
    if (
      this.busy() ||
      this.item.individual ||
      this.mode() !== 'restock' ||
      this.item.ownerId !== this.userId
    )
      return;
    if (this.restockForm.invalid) {
      this.restockForm.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    this.api
      .restockItem(this.item.id, this.restockForm.controls.quantity.value)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => {
          this.mode.set(null);
          this.restockForm.reset({ quantity: 1 });
          this.updated.emit(item);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  loadRestocks() {
    if (this.busy() || this.item.individual) return;
    this.mode.set('restocks');
    this.restocks.set([]);
    this.restocksLoaded.set(false);
    this.error.set('');
    this.busy.set(true);
    this.api
      .restocks(this.item.id)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (history) => {
          this.restocks.set(history);
          this.restocksLoaded.set(true);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  refresh() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .item(this.item.id)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => this.updated.emit(item),
        error: (error) => this.error.set(apiError(error)),
      });
  }

  askArchive() {
    if (this.busy() || this.item.archived || this.item.ownerId !== this.userId) return;
    this.error.set('');
    this.mode.set('archive');
  }

  setArchived(archived: boolean) {
    if (this.busy() || this.item.ownerId !== this.userId || (archived && this.mode() !== 'archive'))
      return;
    this.busy.set(true);
    this.error.set('');
    const request = archived
      ? this.api.archiveItem(this.item.id)
      : this.api.restoreItem(this.item.id);
    request
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => {
          this.mode.set(null);
          this.updated.emit(item);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  askDelete() {
    if (this.item.inUseQuantity > 0) return;
    if (this.busy() || this.item.ownerId !== this.userId) return;
    this.error.set('');
    this.mode.set('delete');
  }

  deleteItem() {
    if (this.item.inUseQuantity > 0) return;
    if (this.busy() || this.mode() !== 'delete' || this.item.ownerId !== this.userId) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .deleteItem(this.item.id)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.mode.set(null);
          this.deleted.emit(this.item.id);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  openMove() {
    if (this.item.archived) return;
    if (this.busy() || this.item.inUseQuantity > 0) return;
    const houseId = this.item.houseId ?? this.item.location?.houseId;
    if (houseId == null) return;
    this.mode.set('move');
    this.moveForm.reset();
    this.destinations.set([]);
    this.error.set('');
    this.busy.set(true);
    this.api
      .locations(houseId)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (locations) =>
          this.destinations.set(
            locations.filter(
              (location) => location.houseId === houseId && location.id !== this.item.location?.id,
            ),
          ),
        error: (error) => this.error.set(apiError(error)),
      });
  }

  move() {
    if (this.item.archived) return;
    if (this.busy() || this.item.inUseQuantity > 0) return;
    if (this.moveForm.invalid) {
      this.moveForm.markAllAsTouched();
      return;
    }
    const destination = this.moveForm.controls.toLocationId.value;
    if (
      destination === null ||
      !this.destinations().some((location) => location.id === destination)
    )
      return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .moveItem(this.item.id, destination, this.item.quantity)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (item) => this.updated.emit(item),
        error: (error) => this.error.set(apiError(error)),
      });
  }

  loadMovements() {
    if (this.busy()) return;
    this.mode.set('movements');
    this.movementHistory.set([]);
    this.movementsLoaded.set(false);
    this.error.set('');
    this.busy.set(true);
    this.api
      .movements(this.item.id)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (history) => {
          this.movementHistory.set(history);
          this.movementsLoaded.set(true);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  loadHistory() {
    if (this.busy()) return;
    this.mode.set('history');
    this.error.set('');
    this.historyLoaded.set(false);
    this.history.set([]);
    this.busy.set(true);
    this.api
      .consumptions(this.item.id)
      .pipe(
        takeUntil(this.selectionChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (history) => {
          this.history.set(history);
          this.historyLoaded.set(true);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }
}
