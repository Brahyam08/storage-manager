import { DOCUMENT } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { onVisibleRefresh } from './visible-refresh';

function assets(document: Document): string | null {
  if (!document.querySelector('app-root')) return null;
  const files = Array.from(document.querySelectorAll('script[src], link[rel="stylesheet"]'))
    .map((node) => node.getAttribute('src') ?? node.getAttribute('href') ?? '')
    .filter((file) => /^(?:\.\/|\/)?(?:main|polyfills|chunk|styles)-[\w-]+\.(?:js|css)$/.test(file))
    .sort();
  return files.some((file) => /(?:main|polyfills)-/.test(file)) ? files.join('|') : null;
}

@Injectable({ providedIn: 'root' })
export class AppUpdateService {
  private readonly document = inject(DOCUMENT);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly loadedAssets = assets(this.document);
  private checking = false;
  readonly available = signal(false);

  constructor() {
    onVisibleRefresh(() => this.check(), 60_000);
  }

  check() {
    if (!this.loadedAssets || this.checking || this.available()) return;
    this.checking = true;
    this.http
      .get('/index.html', { responseType: 'text', headers: { 'Cache-Control': 'no-cache' } })
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.checking = false;
        }),
      )
      .subscribe({
        next: (html) => {
          const latest = assets(new DOMParser().parseFromString(html, 'text/html'));
          if (latest && latest !== this.loadedAssets) this.available.set(true);
        },
        error: () => {
          /* An interrupted deployment must not produce a false update notice. */
        },
      });
  }

  reload() {
    this.document.defaultView?.location.reload();
  }
}
