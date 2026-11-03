package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.*;
public record UseItemRequest(@NotNull @Min(1) Integer quantity) {}
