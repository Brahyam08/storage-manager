import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, switchMap, tap } from 'rxjs';
import { AuthService } from '../core/auth.service';
import { apiError } from '../core/api-error';
import { passwordByteLimit } from './password.validator';

@Component({
  selector: 'app-auth-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './auth-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuthPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder);
  readonly registering = this.route.snapshot.routeConfig?.path === 'registro';
  readonly registered = this.route.snapshot.queryParamMap.has('registered');
  readonly expired = this.route.snapshot.queryParamMap.has('expired');
  readonly recovering = signal(!this.registering && !!this.auth.token());
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
    remember: [false],
    username: [
      '',
      this.registering
        ? [Validators.required, Validators.pattern(/\S/), Validators.maxLength(255)]
        : [],
    ],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: [
      '',
      [
        Validators.required,
        Validators.pattern(/\S/),
        Validators.maxLength(72),
        passwordByteLimit,
        ...(this.registering ? [Validators.minLength(8)] : []),
      ],
    ],
  });

  constructor() {
    if (this.recovering()) this.resume();
  }

  resume() {
    if (this.busy()) return;
    this.busy.set(true);
    this.error.set('');
    this.auth
      .restore()
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((restored) => {
        if (restored) {
          const requested = this.route.snapshot.queryParamMap.get('returnTo') ?? '/inventario';
          const destination = /^\/(inventario|perfil|objetos\/[1-9]\d*)(\?[^#]*)?$/.test(requested)
            ? requested
            : '/inventario';
          void this.router.navigateByUrl(destination);
        } else if (this.auth.token()) {
          this.error.set(
            'No podemos comprobar tu sesión ahora. Revisa la conexión y vuelve a intentarlo.',
          );
        } else {
          this.recovering.set(false);
          this.error.set('Tu sesión ha terminado. Vuelve a entrar para continuar.');
        }
      });
  }

  useAnotherAccount() {
    if (this.busy()) return;
    this.auth.clear();
    this.recovering.set(false);
    this.error.set('');
  }

  trimEmail() {
    this.form.controls.email.setValue(this.form.controls.email.value.trim());
  }

  submit() {
    this.trimEmail();
    if (this.form.invalid || this.busy()) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    const { username, email, password, remember } = this.form.getRawValue();
    let accountCreated = false;
    const login = this.registering
      ? this.auth.register(username, email, password).pipe(
          tap(() => {
            accountCreated = true;
          }),
          switchMap(() => this.auth.login(email, password, remember)),
        )
      : this.auth.login(email, password, remember);
    login
      .pipe(
        finalize(() => this.busy.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: () => {
          this.form.controls.password.reset();
          void this.router.navigate(['/inventario']);
        },
        error: (error) => {
          if (this.registering && accountCreated) {
            this.form.controls.password.reset();
            void this.router.navigate(['/acceso'], { queryParams: { registered: '1' } });
          } else {
            this.error.set(apiError(error));
          }
        },
      });
  }
}
