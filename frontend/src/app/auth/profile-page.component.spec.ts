import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { signal } from '@angular/core';
import { AuthService } from '../core/auth.service';
import { ProfilePageComponent } from './profile-page.component';

describe('Own profile', () => {
  it('shows the verified account and allows signing out', () => {
    const clear = vi.fn();
    TestBed.configureTestingModule({
      imports: [ProfilePageComponent],
      providers: [
        provideRouter([]),
        {
          provide: AuthService,
          useValue: {
            user: signal({ id: 7, username: 'Alice', email: 'alice@example.test' }),
            clear,
          },
        },
      ],
    });
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(ProfilePageComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Alice');
    expect(fixture.nativeElement.textContent).toContain('alice@example.test');
    fixture.nativeElement.querySelector('button').click();
    expect(clear).toHaveBeenCalledOnce();
    expect(router.navigate).toHaveBeenCalledWith(['/acceso']);
    fixture.destroy();
  });
});
