package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.Item;
import com.brahyam.storagemanager.entity.ItemVisibility;

public record ItemResponse(Long id, String name, String description, String category, Integer quantity,
                           Long ownerId, ItemVisibility visibility, LocationResponse location, boolean outOfStock) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(item.getId(), item.getName(), item.getDescription(), item.getCategory(),
                item.getQuantity(), item.getOwner().getId(), item.getVisibility(), LocationResponse.from(item.getLocation()), item.getQuantity() == 0);
    }
}
