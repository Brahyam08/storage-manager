package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.ItemMovement;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ItemMovementResponse(Long id, LocationResponse fromLocation, LocationResponse toLocation,
                                   Integer quantity, OffsetDateTime movedAt) {
    public static ItemMovementResponse from(ItemMovement movement) {
        return new ItemMovementResponse(movement.getId(), LocationResponse.from(movement.getFromLocation()),
                LocationResponse.from(movement.getToLocation()), movement.getQuantity(), movement.getMovedAt().atOffset(ZoneOffset.UTC));
    }
}
