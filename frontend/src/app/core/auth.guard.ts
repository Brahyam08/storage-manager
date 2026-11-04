import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.user()) return true;
  if (!auth.token()) return router.createUrlTree(['/acceso']);
  return auth.restore().pipe(
    map(
      (restored) =>
        restored ||
        router.createUrlTree(['/acceso'], {
          queryParams: auth.token() ? { returnTo: state.url } : { expired: '1' },
        }),
    ),
  );
};
