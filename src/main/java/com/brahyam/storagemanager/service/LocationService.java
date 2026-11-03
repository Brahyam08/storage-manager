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
    private final ItemMovementRepository movements;
    private final ItemConsumptionRepository consumptions;

    public List<LocationResponse> findAll(Long houseId, User user) {
        houseService.findById(houseId, user);
        return locationRepository.findAllByHouseIdOrderByIdAsc(houseId).stream()
                .map(LocationResponse::from).toList();
    }

    public Location findById(Long id, User user) {
        Long houseId = locationRepository.findHouseIdById(id).orElseThrow(this::notFound);
        houseService.findById(houseId, user);
        return inHouse(id, houseId);
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
        Location location = lockForUpdate(id, user);
        if (locationRepository.existsByParentId(id) || items.existsByLocationId(id)
                || movements.existsByFromLocationIdOrToLocationId(id, id) || consumptions.existsByLocationId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La ubicación contiene otras ubicaciones, objetos o historial");
        }
        locationRepository.delete(location);
    }

    @Transactional
    public Location lockForUpdate(Long id, User user) {
        Long houseId = locationRepository.findHouseIdById(id).orElseThrow(this::notFound);
        houseService.lockForUpdate(houseId, user);
        return inHouse(id, houseId);
    }

    private Location inHouse(Long id, Long houseId) {
        return locationRepository.findByIdAndHouseId(id, houseId).orElseThrow(this::notFound);
    }

    private void apply(Location location, LocationRequest request) {
        Location parent = request.getParentId() == null ? null
                : inHouse(request.getParentId(), location.getHouse().getId());
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
