package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.ItemUse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ItemUseResponse(Long id, Integer quantity, Long usedById, OffsetDateTime takenAt,
                              OffsetDateTime returnedAt, LocationResponse location) {
    public static ItemUseResponse from(ItemUse use) {
        return new ItemUseResponse(use.getId(), use.getQuantity(), use.getUsedBy().getId(),
                use.getTakenAt().atOffset(ZoneOffset.UTC), use.getReturnedAt() == null ? null : use.getReturnedAt().atOffset(ZoneOffset.UTC), LocationResponse.from(use.getLocation()));
    }
}
