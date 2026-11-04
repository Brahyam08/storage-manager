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
      <section class="panel install-guide" aria-labelledby="install-title">
        <p class="eyebrow">EN TU MÓVIL</p>
        <h2 id="install-title">Añadir a la pantalla de inicio</h2>
        <p>Abre Storage Manager desde su icono, como una aplicación.</p>
        <ul>
          <li>
            <strong>iPhone:</strong> en Safari, abre Compartir y elige «Añadir a pantalla de
            inicio». Si aparece, activa «Abrir como app web».
          </li>
          <li>
            <strong>Android:</strong> en Chrome, abre el menú y busca «Instalar aplicación» o
            «Añadir a pantalla de inicio».
          </li>
        </ul>
        <p class="muted">Las opciones dependen de tu navegador y de su versión.</p>
        @if (!secureContext) {
          <p class="muted">
            Esta dirección de red local usa HTTP. Puedes probar la web; para completar la
            instalación necesitaremos una dirección HTTPS.
          </p>
        }
        <p class="muted">
          Necesitas conexión para consultar y modificar tu inventario. Al abrir la aplicación
          instalada por primera vez puede que tengas que iniciar sesión.
        </p>
      </section>
    </main>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePageComponent {
  readonly auth = inject(AuthService);
  readonly secureContext = window.isSecureContext;
  private readonly router = inject(Router);
  logout() {
    this.auth.clear();
    void this.router.navigate(['/acceso']);
  }
}
