import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { House, InventoryItem } from './models';

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

  items(houseId: number, search: string) {
    let params = new HttpParams().set('houseId', houseId);
    if (search) params = params.set('search', search);
    return this.http.get<InventoryItem[]>('/api/items', { params });
  }
}
