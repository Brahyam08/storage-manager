package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.Location;
import java.util.ArrayDeque;

public record LocationResponse(Long id, String name, String description,
                               Long houseId, Long parentId, String path, boolean archived, boolean deleted) {
    public static LocationResponse from(Location location) {
        if (location == null) return null;
        ArrayDeque<String> names = new ArrayDeque<>();
        for (Location current = location; current != null; current = current.getParent()) {
            names.addFirst(current.getName());
        }
        return new LocationResponse(location.getId(), location.getName(), location.getDescription(),
                location.getHouse().getId(), location.getParent() == null ? null : location.getParent().getId(),
                location.isDeleted() ? location.getDeletedPath() + " (eliminada)" : String.join(" → ", names), location.isArchived(), location.isDeleted());
    }
}
