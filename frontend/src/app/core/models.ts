export interface UserProfile {
  id: number;
  username: string;
  email: string;
  role: 'ADMIN' | 'VIEWER';
}

export interface House {
  id: number;
  name: string;
  ownerId: number;
}

export interface StorageLocation {
  id: number;
  name: string;
  description: string | null;
  houseId: number;
  parentId: number | null;
  path: string;
}

export interface InventoryItem {
  id: number;
  name: string;
  description: string | null;
  category: string | null;
  quantity: number;
  ownerId: number;
  visibility: 'PRIVATE' | 'HOUSE_SHARED';
  location: StorageLocation;
  outOfStock: boolean;
}

export interface CreateLocation {
  name: string;
  description: string;
  houseId: number;
  parentId: number | null;
}

export interface CreateItem {
  name: string;
  description: string;
  category: string;
  quantity: number;
  locationId: number;
  visibility: InventoryItem['visibility'];
}

export interface ItemConsumption {
  id: number;
  quantity: number;
  remainingQuantity: number;
  consumedById: number;
  consumedAt: string;
  location: StorageLocation;
}
