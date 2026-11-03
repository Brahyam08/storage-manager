import {
  AfterViewInit,
  ElementRef,
  ViewChild,
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
import { Subject, finalize, takeUntil } from 'rxjs';
import { InventoryApiService } from '../core/inventory-api.service';
import { apiError } from '../core/api-error';
import { reusableQuantity } from './item-kind.validator';
import { InventoryItem, ItemConsumption, ItemKind } from '../core/models';

@Component({
  selector: 'app-item-detail',
  imports: [ReactiveFormsModule, DatePipe],
  templateUrl: './item-detail.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ItemDetailComponent implements OnChanges, AfterViewInit {
  @Input({ required: true }) item!: InventoryItem;
  @Input({ required: true }) userId!: number;
  @ViewChild('detailTitle') private title?: ElementRef<HTMLHeadingElement>;
  readonly updated = output<InventoryItem>();
  readonly closed = output<void>();
  private readonly api = inject(InventoryApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly selectionChanged = new Subject<void>();
  readonly busy = signal(false);
  readonly error = signal('');
  readonly mode = signal<'edit' | 'consume' | 'history' | null>(null);
  readonly history = signal<ItemConsumption[]>([]);
  readonly historyLoaded = signal(false);
  readonly editForm = this.fb.nonNullable.group(
    {
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

  ngOnChanges() {
    this.selectionChanged.next();
    this.mode.set(null);
    this.error.set('');
    this.history.set([]);
    this.historyLoaded.set(false);
    this.editForm.reset({
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
    this.title?.nativeElement.focus();
  }

  ngAfterViewInit() {
    this.title?.nativeElement.focus();
  }

  open(mode: 'edit' | 'consume') {
    if (
      this.busy() ||
      (mode === 'edit' && this.item.ownerId !== this.userId) ||
      (mode === 'consume' && (this.item.kind === 'REUSABLE' || this.item.quantity === 0))
    )
      return;
    this.mode.set(mode);
    this.error.set('');
  }

  save() {
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
        name: value.name.trim(),
        description: value.description.trim(),
        category: value.category.trim(),
        locationId: this.item.location.id,
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
