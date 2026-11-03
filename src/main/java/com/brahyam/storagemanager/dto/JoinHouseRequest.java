package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinHouseRequest(@NotBlank @Size(max = 128) String code) {
}
