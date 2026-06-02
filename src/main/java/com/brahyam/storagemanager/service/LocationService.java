package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.Location;
import com.brahyam.storagemanager.repository.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    public List<Location> findAll() {
        return locationRepository.findAll();
    }

    public Location findById(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ubicación no encontrada"));
    }

    public Location save(Location location) {
        return locationRepository.save(location);
    }

    public Location update(Long id, Location updatedLocation) {
        Location existing = findById(id);
        existing.setName(updatedLocation.getName());
        existing.setDescription(updatedLocation.getDescription());
        return locationRepository.save(existing);
    }

    public void deleteById(Long id) {
        findById(id);
        locationRepository.deleteById(id);
    }

}