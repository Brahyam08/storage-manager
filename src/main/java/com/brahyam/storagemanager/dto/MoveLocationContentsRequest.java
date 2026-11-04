package com.brahyam.storagemanager.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record MoveLocationContentsRequest(
        @NotNull @Positive Long toLocationId,
        @NotNull @Size(min = 1, max = 500) List<@NotNull @Valid ItemSnapshot> items) {
    public record ItemSnapshot(@NotNull @Positive Long id, @NotNull @Min(0) Integer quantity) {}
}
