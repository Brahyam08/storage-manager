package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.ItemRestock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ItemRestockResponse(Long id, Integer quantity, Integer resultingQuantity,
        Long restockedById, OffsetDateTime restockedAt, LocationResponse location) {
    public static ItemRestockResponse from(ItemRestock restock) {
        return new ItemRestockResponse(restock.getId(), restock.getQuantity(),
                restock.getResultingQuantity(), restock.getRestockedBy().getId(),
                restock.getRestockedAt().atOffset(ZoneOffset.UTC), LocationResponse.from(restock.getLocation()));
    }
}
