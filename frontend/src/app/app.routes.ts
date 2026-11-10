import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import type { ItemPageComponent } from './inventory/item-page.component';

export const routes: Routes = [
  {
    path: 'acceso',
    loadComponent: () =>
      import('./auth/auth-page.component').then((module) => module.AuthPageComponent),
  },
  {
    path: 'registro',
    loadComponent: () =>
      import('./auth/auth-page.component').then((module) => module.AuthPageComponent),
  },
  {
    path: 'inventario',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./inventory/inventory-page.component').then(
        (module) => module.InventoryPageComponent,
      ),
  },
  {
    path: 'objetos/:id',
    canActivate: [authGuard],
    canDeactivate: [(page: ItemPageComponent) => page.confirmLeaving()],
    loadComponent: () =>
      import('./inventory/item-page.component').then((module) => module.ItemPageComponent),
  },
  {
    path: 'perfil',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./auth/profile-page.component').then((module) => module.ProfilePageComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'inventario' },
  { path: '**', redirectTo: 'inventario' },
];
