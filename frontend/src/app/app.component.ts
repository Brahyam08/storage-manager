import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppUpdateService } from './core/app-update.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    @if (update.available()) {
      <aside class="app-update-notice" role="status">
        <p><strong>Nueva versión disponible.</strong> Guarda tus cambios antes de recargar.</p>
        <button class="button secondary" type="button" (click)="update.reload()">
          Recargar web
        </button>
      </aside>
    }
    <router-outlet />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AppComponent {
  readonly update = inject(AppUpdateService);
}
