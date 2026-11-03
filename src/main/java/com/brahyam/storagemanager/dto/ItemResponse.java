package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.Item;
import com.brahyam.storagemanager.entity.ItemVisibility;
import com.brahyam.storagemanager.entity.ItemKind;

public record ItemResponse(Long id, String name, String description, String category, Integer quantity,
                           Long ownerId, ItemVisibility visibility, LocationResponse location, boolean outOfStock, ItemKind kind, int inUseQuantity, int availableQuantity) {
    public static ItemResponse from(Item item, int inUseQuantity) {
        return new ItemResponse(item.getId(), item.getName(), item.getDescription(), item.getCategory(),
                item.getQuantity(), item.getOwner().getId(), item.getVisibility(), LocationResponse.from(item.getLocation()), item.getKind() == ItemKind.CONSUMABLE && item.getQuantity() == 0, item.getKind(), inUseQuantity, item.getQuantity() - inUseQuantity);
    }
}
