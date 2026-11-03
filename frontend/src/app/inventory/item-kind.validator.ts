import { AbstractControl, ValidationErrors } from '@angular/forms';

export function reusableQuantity(control: AbstractControl): ValidationErrors | null {
  return control.get('kind')?.value === 'REUSABLE' && control.get('quantity')?.value < 1
    ? { reusableQuantity: true }
    : null;
}
