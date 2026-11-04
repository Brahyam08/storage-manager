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
  archived?: boolean;
  id: number;
  name: string;
  description: string | null;
  houseId: number;
  parentId: number | null;
  path: string;
}

export type ItemKind = 'CONSUMABLE' | 'REUSABLE';

export interface InventoryItem {
  archived?: boolean;
  individual?: boolean;
  inUseQuantity: number;
  availableQuantity: number;
  kind: ItemKind;
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
  individual?: boolean;
  kind: ItemKind;
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

export interface ItemMovement {
  id: number;
  fromLocation: StorageLocation;
  toLocation: StorageLocation;
  quantity: number;
  movedAt: string;
}

export interface ItemUse {
  id: number;
  quantity: number;
  usedById: number;
  takenAt: string;
  returnedAt: string | null;
  location: StorageLocation;
}

export interface ItemRestock {
  id: number;
  quantity: number;
  resultingQuantity: number;
  restockedById: number;
  restockedAt: string;
  location: StorageLocation;
}
