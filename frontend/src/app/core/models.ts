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
