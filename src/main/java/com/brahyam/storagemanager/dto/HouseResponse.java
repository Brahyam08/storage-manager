package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.House;

public record HouseResponse(Long id, String name, Long ownerId) {
    public static HouseResponse from(House house) {
        return new HouseResponse(house.getId(), house.getName(), house.getOwner().getId());
    }
}
