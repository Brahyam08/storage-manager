import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { House } from '../core/models';

@Component({
  selector: 'app-workspace-layout',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './workspace-layout.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkspaceLayoutComponent {
  protected readonly auth = inject(AuthService);
  readonly houses = input.required<House[]>();
  readonly houseControl = input.required<FormControl<number | null>>();
  readonly housePlaceholder = input('Tu primera casa');
  readonly managingHouses = input(false);
  readonly showInventory = output<void>();
  readonly showHouses = output<void>();
  readonly signOut = output<void>();
}
