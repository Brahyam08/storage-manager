import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';

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
  { path: '', pathMatch: 'full', redirectTo: 'inventario' },
  { path: '**', redirectTo: 'inventario' },
];
