import { AbstractControl, ValidationErrors } from '@angular/forms';

export function passwordByteLimit(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  return typeof value === 'string' && new TextEncoder().encode(value).length > 72
    ? { passwordByteLimit: true }
    : null;
}
