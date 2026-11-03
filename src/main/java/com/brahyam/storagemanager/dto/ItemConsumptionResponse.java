package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.ItemConsumption;
import java.time.LocalDateTime;

public record ItemConsumptionResponse(Long id, Integer quantity, Integer remainingQuantity,
        Long consumedById, LocalDateTime consumedAt, LocationResponse location) {
    public static ItemConsumptionResponse from(ItemConsumption consumption) {
        return new ItemConsumptionResponse(consumption.getId(), consumption.getQuantity(),
                consumption.getRemainingQuantity(), consumption.getConsumedBy().getId(),
                consumption.getConsumedAt(), LocationResponse.from(consumption.getLocation()));
    }
}
