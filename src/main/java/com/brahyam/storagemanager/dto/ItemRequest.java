package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ItemRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String description;

    private String category;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 1, message = "La cantidad mínima es 1")
    private Integer quantity;

    @NotNull(message = "La ubicación es obligatoria")
    private Long locationId;

}