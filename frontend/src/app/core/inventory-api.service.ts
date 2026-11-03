import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  House,
  InventoryItem,
  StorageLocation,
  CreateLocation,
  CreateItem,
  ItemConsumption,
  ItemMovement,
  ItemUse,
} from './models';

@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly http = inject(HttpClient);

  houses() {
    return this.http.get<House[]>('/api/houses');
  }

  createHouse(name: string) {
    return this.http.post<House>('/api/houses', { name: name.trim() });
  }

  joinHouse(code: string) {
    return this.http.post<House>('/api/houses/join', { code: code.trim() });
  }

  renewInvitation(houseId: number) {
    return this.http.post<{ code: string }>(`/api/houses/${houseId}/invitation`, {});
  }

  locations(houseId: number) {
    return this.http.get<StorageLocation[]>('/api/locations', { params: { houseId } });
  }

  createLocation(request: CreateLocation) {
    return this.http.post<StorageLocation>('/api/locations', request);
  }

  createItem(request: CreateItem) {
    return this.http.post<InventoryItem>('/api/items', request);
  }

  item(id: number) {
    return this.http.get<InventoryItem>(`/api/items/${id}`);
  }

  updateItem(id: number, request: CreateItem) {
    return this.http.put<InventoryItem>(`/api/items/${id}`, request);
  }

  consumeItem(id: number, quantity: number) {
    return this.http.post<InventoryItem>(`/api/items/${id}/consume`, { quantity });
  }

  consumptions(id: number) {
    return this.http.get<ItemConsumption[]>(`/api/items/${id}/consumptions`);
  }

  moveItem(id: number, toLocationId: number, quantity: number) {
    return this.http.patch<InventoryItem>(`/api/items/${id}/move`, null, {
      params: { toLocationId, quantity },
    });
  }

  movements(id: number) {
    return this.http.get<ItemMovement[]>(`/api/items/${id}/history`);
  }

  uses(id: number) {
    return this.http.get<ItemUse[]>(`/api/items/${id}/uses`);
  }

  takeItem(id: number, quantity: number) {
    return this.http.post<ItemUse>(`/api/items/${id}/uses`, { quantity });
  }

  returnItem(id: number, useId: number) {
    return this.http.post<ItemUse>(`/api/items/${id}/uses/${useId}/return`, {});
  }

  items(houseId: number, search: string) {
    let params = new HttpParams().set('houseId', houseId);
    if (search) params = params.set('search', search);
    return this.http.get<InventoryItem[]>('/api/items', { params });
  }
}
