package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LocationRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String name;

    private String description;

}