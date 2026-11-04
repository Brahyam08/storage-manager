package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record RestockItemRequest(
        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "Añade al menos una unidad") Integer quantity) {
}
