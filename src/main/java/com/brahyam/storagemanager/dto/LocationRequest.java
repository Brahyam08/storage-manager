package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LocationRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 255)
    private String name;
    @Size(max = 255)
    private String description;
    @NotNull(message = "La casa es obligatoria")
    private Long houseId;
    private Long parentId;
}
