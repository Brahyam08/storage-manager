import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-profile-page',
  imports: [RouterLink],
  template: `
    <main class="detail-page">
      <nav class="detail-page-nav" aria-label="Navegación del perfil">
        <a class="button secondary" routerLink="/inventario">Volver al inventario</a>
      </nav>
      <section class="panel" aria-labelledby="profile-title">
        <p class="eyebrow">TU CUENTA</p>
        <h1 id="profile-title">Mi perfil</h1>
        <dl class="profile-fields">
          <div>
            <dt>Nombre de usuario</dt>
            <dd>{{ auth.user()?.username }}</dd>
          </div>
          <div>
            <dt>Correo electrónico</dt>
            <dd>{{ auth.user()?.email }}</dd>
          </div>
        </dl>
        <p class="muted">
          Por ahora puedes consultar tus datos. La edición del perfil aún no está disponible.
        </p>
        <button class="button secondary" type="button" (click)="logout()">Cerrar sesión</button>
      </section>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePageComponent {
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  logout() {
    this.auth.clear();
    void this.router.navigate(['/acceso']);
  }
}
