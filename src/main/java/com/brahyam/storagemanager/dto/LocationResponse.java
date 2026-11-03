package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.Location;
import java.util.ArrayDeque;

public record LocationResponse(Long id, String name, String description,
                               Long houseId, Long parentId, String path) {
    public static LocationResponse from(Location location) {
        ArrayDeque<String> names = new ArrayDeque<>();
        for (Location current = location; current != null; current = current.getParent()) {
            names.addFirst(current.getName());
        }
        return new LocationResponse(location.getId(), location.getName(), location.getDescription(),
                location.getHouse().getId(), location.getParent() == null ? null : location.getParent().getId(),
                String.join(" → ", names));
    }
}
