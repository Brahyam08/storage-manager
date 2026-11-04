package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.dto.*;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationService {
    private final LocationRepository locationRepository;
    private final HouseService houseService;
    private final ItemRepository items;

    public List<LocationResponse> findAll(Long houseId, User user) {
        return findAll(houseId, false, user);
    }

    public List<LocationResponse> findAll(Long houseId, boolean includeArchived, User user) {
        houseService.findById(houseId, user);
        return locationRepository.findAllByHouseIdOrderByIdAsc(houseId).stream()
                .filter(location -> !location.isDeleted() && (includeArchived || !location.isArchived()))
                .map(LocationResponse::from).toList();
    }

    public Location findById(Long id, User user) {
        Long houseId = locationRepository.findHouseIdById(id).orElseThrow(this::notFound);
        houseService.findById(houseId, user);
        return inHouse(id, houseId);
    }

    public Location findActiveById(Long id, User user) {
        Location location = findById(id, user);
        requireActive(location);
        return location;
    }

    public LocationResponse findResponse(Long id, User user) {
        return LocationResponse.from(findById(id, user));
    }

    @Transactional
    public LocationResponse create(LocationRequest request, User user) {
        House house = houseService.lockForUpdate(request.getHouseId(), user);
        Location location = new Location();
        location.setHouse(house);
        apply(location, request);
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse update(Long id, LocationRequest request, User user) {
        Location location = lockForUpdate(id, user);
        if (!location.getHouse().getId().equals(request.getHouseId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se puede cambiar la casa de una ubicación");
        }
        apply(location, request);
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public void deleteById(Long id, User user) {
        Location location = lockedLocation(id, user);
        String path = LocationResponse.from(location).path();
        for (Item item : items.findAllByLocationId(id)) {
            item.setHouse(location.getHouse());
            item.setLocation(null);
        }
        for (Location child : locationRepository.findAllByHouseIdOrderByIdAsc(location.getHouse().getId())) {
            if (!child.isDeleted() && child.getParent() != null && child.getParent().getId().equals(id)) {
                child.setParent(null);
            }
        }
        // Retain a historical reference, excluded from every usable location endpoint.
        location.setDeletedPath(path);
        location.setDeleted(true);
        location.setParent(null);
        locationRepository.flush();
    }

    @Transactional
    public Location lockForUpdate(Long id, User user) {
        Location location = lockedLocation(id, user);
        requireActive(location);
        return location;
    }

    @Transactional
    public LocationResponse archive(Long id, User user) {
        Location location = lockedLocation(id, user);
        if (!location.isArchived() && (items.existsByLocationIdAndArchivedFalse(id)
                || locationRepository.existsByParentIdAndArchivedFalse(id))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Traslada o archiva los objetos y archiva o reorganiza las ubicaciones interiores antes de archivar");
        }
        location.setArchived(true);
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse restore(Long id, User user) {
        Location location = lockedLocation(id, user);
        if (location.getParent() != null) requireActive(location.getParent());
        location.setArchived(false);
        return LocationResponse.from(locationRepository.save(location));
    }

    private Location lockedLocation(Long id, User user) {
        Long houseId = locationRepository.findHouseIdById(id).orElseThrow(this::notFound);
        houseService.lockForUpdate(houseId, user);
        return inHouse(id, houseId);
    }

    private void requireActive(Location location) {
        if (location.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La ubicación está archivada. Restáurala antes de usarla");
        }
    }

    private Location inHouse(Long id, Long houseId) {
        return locationRepository.findByIdAndHouseId(id, houseId)
                .filter(location -> !location.isDeleted()).orElseThrow(this::notFound);
    }

    private void apply(Location location, LocationRequest request) {
        Location parent = request.getParentId() == null ? null
                : inHouse(request.getParentId(), location.getHouse().getId());
        if (parent != null) requireActive(parent);
        for (Location ancestor = parent; ancestor != null; ancestor = ancestor.getParent()) {
            if (ancestor.getId().equals(location.getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La jerarquía no puede contener ciclos");
            }
        }
        location.setName(request.getName().strip());
        location.setDescription(request.getDescription());
        location.setParent(parent);
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Ubicación no encontrada");
    }
}
