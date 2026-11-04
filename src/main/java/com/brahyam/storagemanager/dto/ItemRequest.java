package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import jakarta.validation.constraints.Size;
import com.brahyam.storagemanager.entity.ItemVisibility;
import com.brahyam.storagemanager.entity.ItemKind;

@Data
public class ItemRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255)
    private String name;

    @Size(max = 255)
    private String description;

    @Size(max = 255)
    private String category;

    @NotNull(message = "La cantidad es obligatoria")
    @Min(value = 0, message = "La cantidad no puede ser negativa")
    private Integer quantity;

    @NotNull(message = "La ubicación es obligatoria")
    private Long locationId;

    private ItemVisibility visibility;
    private ItemKind kind;
    private Boolean individual;

}