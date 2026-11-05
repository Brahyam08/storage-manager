import { DOCUMENT } from '@angular/common';
import { DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, fromEvent, interval, merge, throttleTime } from 'rxjs';

/** Poll only visible pages, and check immediately when the user returns. */
export function onVisibleRefresh(refresh: () => void) {
  const document = inject(DOCUMENT);
  const window = document.defaultView;
  if (!window) return;
  merge(interval(15_000), fromEvent(document, 'visibilitychange'), fromEvent(window, 'focus'))
    .pipe(
      filter(() => document.visibilityState === 'visible'),
      throttleTime(1_000),
      takeUntilDestroyed(inject(DestroyRef)),
    )
    .subscribe(refresh);
}
