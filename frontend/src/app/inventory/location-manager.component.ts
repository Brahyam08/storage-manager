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
import { InventoryItem, StorageLocation } from '../core/models';
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
  readonly showArchived = signal(false);
  readonly visibleLocations = computed(() =>
    this.locations().filter((location) => this.showArchived() || !location.archived),
  );
  readonly transferring = signal<StorageLocation | null>(null);
  readonly transferItems = signal<InventoryItem[]>([]);
  readonly transferReady = signal(false);
  readonly transferForm = this.fb.group({
    toLocationId: this.fb.control<number | null>(null, Validators.required),
  });
  readonly destinations = computed(() =>
    this.locations().filter(
      (location) => !location.archived && location.id !== this.transferring()?.id,
    ),
  );
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
      if (candidate.archived) return false;
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
    this.showArchived.set(false);
    this.transferItems.set([]);
    this.transferForm.reset();
    this.transferring.set(null);
    this.transferReady.set(false);
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
      .locations(this.houseId, true)
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
      location.archived ||
      this.busy() ||
      this.loading() ||
      this.loadError() ||
      !this.locations().some((entry) => entry.id === location.id)
    )
      return;
    this.error.set('');
    this.notice.set('');
    this.pendingDelete.set(null);
    this.transferring.set(null);
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
    this.transferring.set(null);
    this.transferReady.set(false);
    this.editing.set(null);
    this.pendingDelete.set(location);
  }

  toggleArchive(location: StorageLocation) {
    if (
      this.busy() ||
      this.loading() ||
      this.loadError() ||
      !this.locations().some((entry) => entry.id === location.id)
    )
      return;
    this.busy.set(true);
    this.error.set('');
    this.transferring.set(null);
    this.transferReady.set(false);
    this.editing.set(null);
    this.pendingDelete.set(null);
    const request = location.archived
      ? this.api.restoreLocation(location.id)
      : this.api.archiveLocation(location.id);
    request
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: () => {
          this.notice.set(
            location.archived
              ? 'Ubicación restaurada.'
              : 'Ubicación archivada. Puedes restaurarla desde Mostrar archivadas.',
          );
          this.changed.emit();
          this.load();
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  startTransfer(location: StorageLocation) {
    if (
      location.archived ||
      this.busy() ||
      this.loading() ||
      this.loadError() ||
      !this.locations().some((entry) => entry.id === location.id)
    )
      return;
    this.editing.set(null);
    this.pendingDelete.set(null);
    this.notice.set('');
    this.transferring.set(location);
    this.transferForm.reset();
    this.refreshTransfer();
  }

  refreshTransfer() {
    const source = this.transferring();
    if (!source || this.busy()) return;
    this.busy.set(true);
    this.transferReady.set(false);
    this.transferItems.set([]);
    this.error.set('');
    this.api
      .locationContents(source.id)
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (items) => {
          this.transferItems.set(items);
          this.transferReady.set(true);
        },
        error: (error) => this.error.set(apiError(error)),
      });
  }

  confirmTransfer() {
    const source = this.transferring();
    const destination = this.destinations().find(
      (location) => location.id === this.transferForm.getRawValue().toLocationId,
    );
    if (
      !source ||
      !destination ||
      this.busy() ||
      !this.transferReady() ||
      !this.transferItems().length
    )
      return;
    this.busy.set(true);
    this.error.set('');
    const snapshot = this.transferItems().map(({ id, quantity }) => ({ id, quantity }));
    this.api
      .moveLocationContents(source.id, destination.id, snapshot)
      .pipe(
        takeUntil(this.houseChanged),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.busy.set(false)),
      )
      .subscribe({
        next: (items) => {
          this.transferring.set(null);
          this.transferReady.set(false);
          this.notice.set(
            `${items.length} ${items.length === 1 ? 'objeto trasladado' : 'objetos trasladados'} a ${destination.path}.`,
          );
          this.changed.emit();
        },
        error: (error) => {
          this.transferReady.set(false);
          this.error.set(apiError(error));
        },
      });
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
