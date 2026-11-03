import { HttpErrorResponse } from '@angular/common/http';

export function apiError(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'No hemos podido completar la operación.';
  if (error.status === 0)
    return 'No podemos conectar con el servidor. Inténtalo de nuevo en unos momentos.';
  if (error.status === 401) return 'Correo o contraseña incorrectos.';
  if (error.status === 403) return 'No tienes permiso para realizar esta operación.';
  if (error.status === 404) return 'No encontramos ese recurso o no tienes acceso.';
  if (error.status === 429) {
    const seconds = Number(error.headers.get('Retry-After'));
    return seconds > 0
      ? `Demasiados intentos. Vuelve a intentarlo en ${seconds} segundos.`
      : 'Demasiados intentos. Espera un poco y vuelve a intentarlo.';
  }
  if (
    (error.status === 400 || error.status === 409) &&
    error.error &&
    typeof error.error === 'object'
  ) {
    const messages = Object.values(error.error).filter(
      (value): value is string => typeof value === 'string',
    );
    if (messages.length) return messages.join(' ');
  }
  return 'No hemos podido completar la operación. Inténtalo de nuevo.';
}
