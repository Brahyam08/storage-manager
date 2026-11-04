package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.ItemConsumption;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record ItemConsumptionResponse(Long id, Integer quantity, Integer remainingQuantity,
        Long consumedById, OffsetDateTime consumedAt, LocationResponse location) {
    public static ItemConsumptionResponse from(ItemConsumption consumption) {
        return new ItemConsumptionResponse(consumption.getId(), consumption.getQuantity(),
                consumption.getRemainingQuantity(), consumption.getConsumedBy().getId(),
                consumption.getConsumedAt().atOffset(ZoneOffset.UTC), LocationResponse.from(consumption.getLocation()));
    }
}
