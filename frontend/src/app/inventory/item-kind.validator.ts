import { AbstractControl, ValidationErrors } from '@angular/forms';

export function reusableQuantity(control: AbstractControl): ValidationErrors | null {
  const reusable = control.get('kind')?.value === 'REUSABLE';
  const quantity = control.get('quantity')?.value;
  if (reusable && quantity < 1) return { reusableQuantity: true };
  if (reusable && control.get('individual')?.value && quantity !== 1) {
    return { individualQuantity: true };
  }
  return null;
}
