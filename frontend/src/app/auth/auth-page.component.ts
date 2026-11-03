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
  readonly busy = signal(false);
  readonly error = signal('');
  readonly form = this.fb.nonNullable.group({
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
    const { username, email, password } = this.form.getRawValue();
    let accountCreated = false;
    const login = this.registering
      ? this.auth.register(username, email, password).pipe(
          tap(() => {
            accountCreated = true;
          }),
          switchMap(() => this.auth.login(email, password)),
        )
      : this.auth.login(email, password);
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
