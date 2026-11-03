import {
  ElementRef,
  ViewChild,
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Input,
  OnChanges,
  computed,
  inject,
  output,
  signal,
} from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, finalize, takeUntil } from 'rxjs';
import { InventoryApiService } from '../core/inventory-api.service';
import { StorageLocation } from '../core/models';
import { apiError } from '../core/api-error';

@Component({
  selector: 'app-location-manager',
  imports: [ReactiveFormsModule],
  templateUrl: './location-manager.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LocationManagerComponent implements OnChanges {
  @Input({ required: true }) houseId!: number;
  @ViewChild('actionTitle') set actionTitle(title: ElementRef<HTMLHeadingElement> | undefined) {
    title?.nativeElement.focus();
  }
  readonly changed = output<void>();
  private readonly api = inject(InventoryApiService);
  private readonly fb = inject(FormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly houseChanged = new Subject<void>();
  readonly locations = signal<StorageLocation[]>([]);
  readonly editing = signal<StorageLocation | null>(null);
  readonly pendingDelete = signal<StorageLocation | null>(null);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly loadError = signal('');
  readonly notice = signal('');
  readonly form = this.fb.group({
    name: this.fb.nonNullable.control('', [
      Validators.required,
      Validators.pattern(/\S/),
      Validators.maxLength(255),
    ]),
    description: this.fb.nonNullable.control('', Validators.maxLength(255)),
    parentId: this.fb.control<number | null>(null),
  });
  readonly parents = computed(() =>
    this.locations().filter((candidate) => {
      const editing = this.editing();
      if (!editing) return true;
      const visited = new Set<number>();
      let current: StorageLocation | undefined = candidate;
      while (current) {
        if (current.id === editing.id || visited.has(current.id)) return false;
        visited.add(current.id);
        const parentId: number | null = current.parentId;
        current = this.locations().find((location) => location.id === parentId);
      }
      return true;
    }),
  );

  ngOnChanges() {
    this.houseChanged.next();
    this.locations.set([]);
    this.editing.set(null);
    this.pendingDelete.set(null);
    this.form.reset();
    this.error.set('');
    this.notice.set('');
    this.load();
  }

  load() {
    if (this.loading()) return;
    this.loading.set(true);
    this.loadError.set('');
    this.api
      .locations(this.houseId)
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (locations) =>
          this.locations.set(locations.filter((location) => location.houseId === this.houseId)),
        error: (error) => this.loadError.set(apiError(error)),
      });
  }

  edit(location: StorageLocation) {
    if (
      this.busy() ||
      this.loading() ||
      this.loadError() ||
      !this.locations().some((entry) => entry.id === location.id)
    )
      return;
    this.error.set('');
    this.notice.set('');
    this.pendingDelete.set(null);
    this.editing.set(location);
    this.form.reset({
      name: location.name,
      description: location.description ?? '',
      parentId: location.parentId,
    });
  }

  save() {
    const location = this.editing();
    if (!location || this.busy() || this.loading() || this.loadError()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.parentId !== null && !this.parents().some((parent) => parent.id === value.parentId))
      return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .updateLocation(location.id, {
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
        next: () => {
          this.editing.set(null);
          this.notice.set('Ubicación actualizada.');
          this.changed.emit();
          this.load();
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  askDelete(location: StorageLocation) {
    if (
      this.busy() ||
      this.loading() ||
      this.loadError() ||
      !this.locations().some((entry) => entry.id === location.id)
    )
      return;
    this.error.set('');
    this.notice.set('');
    this.editing.set(null);
    this.pendingDelete.set(location);
  }

  confirmDelete() {
    const location = this.pendingDelete();
    if (!location || this.busy() || this.loading() || this.loadError()) return;
    this.busy.set(true);
    this.error.set('');
    this.api
      .deleteLocation(location.id)
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.pendingDelete.set(null);
          this.notice.set('Ubicación eliminada.');
          this.changed.emit();
          this.load();
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }
}
