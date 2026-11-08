package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HouseRequest(@NotBlank(message = "El nombre es obligatorio")
                           @Size(max = 255, message = "Usa como máximo 255 caracteres") String name) {
}
