import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, fromEvent, interval, merge, throttleTime } from 'rxjs';

/** Poll only visible pages, and check immediately when the user returns. */
export function onVisibleRefresh(refresh: () => void, intervalMs = 15_000) {
  const document = inject(DOCUMENT);
  const window = document.defaultView;
  if (!window) return;
  merge(interval(intervalMs), fromEvent(document, 'visibilitychange'), fromEvent(window, 'focus'))
    .pipe(
      filter(() => document.visibilityState === 'visible'),
      throttleTime(1_000),
      takeUntilDestroyed(inject(DestroyRef)),
    )
    .subscribe(refresh);
}
