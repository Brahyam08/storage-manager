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
  deleted?: boolean;
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
  houseId?: number;
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
  location: StorageLocation | null;
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
  locationId: number | null;
  visibility: InventoryItem['visibility'];
}

export interface ItemConsumption {
  id: number;
  quantity: number;
  remainingQuantity: number;
  consumedById: number;
  consumedAt: string;
  location: StorageLocation | null;
}

export interface ItemMovement {
  id: number;
  fromLocation: StorageLocation | null;
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
  location: StorageLocation | null;
}

export interface ItemRestock {
  id: number;
  quantity: number;
  resultingQuantity: number;
  restockedById: number;
  restockedAt: string;
  location: StorageLocation | null;
}
