package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.NotBlank;

public record HouseRequest(@NotBlank(message = "El nombre es obligatorio") String name) {
}
