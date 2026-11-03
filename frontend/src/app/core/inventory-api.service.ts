import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import {
  House,
  InventoryItem,
  StorageLocation,
  CreateLocation,
  CreateItem,
  ItemConsumption,
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

  items(houseId: number, search: string) {
    let params = new HttpParams().set('houseId', houseId);
    if (search) params = params.set('search', search);
    return this.http.get<InventoryItem[]>('/api/items', { params });
  }
}
