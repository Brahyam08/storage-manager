import { FormControl } from '@angular/forms';
import { passwordByteLimit } from './password.validator';

describe('Password byte limit', () => {
  it('accepts exactly 72 UTF-8 bytes without trimming', () => {
    expect(passwordByteLimit(new FormControl('é'.repeat(36)))).toBeNull();
    expect(passwordByteLimit(new FormControl(' '.repeat(72)))).toBeNull();
  });
  it('rejects more than 72 bytes even with fewer characters', () => {
    expect(passwordByteLimit(new FormControl('é'.repeat(36) + 'a'))).toEqual({
      passwordByteLimit: true,
    });
    expect(passwordByteLimit(new FormControl('😀'.repeat(19)))).toEqual({
      passwordByteLimit: true,
    });
  });
});
