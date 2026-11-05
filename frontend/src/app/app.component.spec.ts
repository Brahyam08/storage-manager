import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AppComponent } from './app.component';
import { AppUpdateService } from './core/app-update.service';

it('shows a non-blocking notice and reloads only on explicit click', () => {
  const update = { available: signal(false), reload: vi.fn() };
  TestBed.configureTestingModule({
    imports: [AppComponent],
    providers: [provideRouter([]), { provide: AppUpdateService, useValue: update }],
  });
  const fixture = TestBed.createComponent(AppComponent);
  fixture.detectChanges();
  expect(fixture.nativeElement.querySelector('[role="status"]')).toBeNull();
  update.available.set(true);
  fixture.detectChanges();
  expect(fixture.nativeElement.textContent).toContain('Guarda tus cambios');
  expect(update.reload).not.toHaveBeenCalled();
  fixture.nativeElement.querySelector('button').click();
  expect(update.reload).toHaveBeenCalledTimes(1);
});
