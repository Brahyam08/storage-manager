package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.ItemUse;
import java.time.LocalDateTime;

public record ItemUseResponse(Long id, Integer quantity, Long usedById, LocalDateTime takenAt,
                              LocalDateTime returnedAt, LocationResponse location) {
    public static ItemUseResponse from(ItemUse use) {
        return new ItemUseResponse(use.getId(), use.getQuantity(), use.getUsedBy().getId(),
                use.getTakenAt(), use.getReturnedAt(), LocationResponse.from(use.getLocation()));
    }
}
