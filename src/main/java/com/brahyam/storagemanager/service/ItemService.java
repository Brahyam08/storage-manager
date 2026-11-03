package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.dto.*;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository items;
    private final ItemMovementRepository movements;
    private final ItemConsumptionRepository consumptions;
    private final LocationService locations;
    private final HouseService houses;

    public ItemResponse findById(Long id, User user) {
        return ItemResponse.from(requireAccessible(id, user));
    }

    Item requireAccessible(Long id, User user) {
        return items.findAccessibleById(id, user.getId()).orElseThrow(this::notFound);
    }

    public List<ItemResponse> findWithFilters(Long houseId, String search, String category, Long locationId, User user) {
        if (houseId != null) houses.findById(houseId, user);
        if (locationId != null) locations.findById(locationId, user);
        return items.findAccessibleWithFilters(user.getId(), houseId, search, category, locationId).stream()
                .map(ItemResponse::from).toList();
    }

    @Transactional
    public ItemResponse create(ItemRequest request, User user) {
        Location location = locations.lockForUpdate(request.getLocationId(), user);
        Item item = new Item();
        item.setOwner(user);
        item.setLocation(location);
        item.setVisibility(request.getVisibility() == null ? ItemVisibility.PRIVATE : request.getVisibility());
        applyDetails(item, request);
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public ItemResponse update(Long id, ItemRequest request, User user) {
        Item item = forUpdate(id, user);
        requireOwner(item, user);
        if (!item.getLocation().getId().equals(request.getLocationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa el movimiento para cambiar la ubicación");
        }
        applyDetails(item, request);
        if (request.getVisibility() != null) item.setVisibility(request.getVisibility());
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public void deleteById(Long id, User user) {
        Item item = forUpdate(id, user);
        requireOwner(item, user);
        if (movements.existsByItemId(id) || consumptions.existsByItemId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El objeto tiene historial");
        }
        items.delete(item);
    }

    @Transactional
    public ItemResponse move(Long id, Long toLocationId, Integer quantity, User user) {
        Item item = forUpdate(id, user);
        if (quantity == null || quantity < 1 || !quantity.equals(item.getQuantity())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se debe mover la cantidad completa del objeto");
        }
        Location destination = locations.findById(toLocationId, user);
        if (!item.getLocation().getHouse().getId().equals(destination.getHouse().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pueden mover objetos entre casas");
        }
        if (item.getLocation().getId().equals(destination.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El objeto ya está en esa ubicación");
        }
        ItemMovement movement = new ItemMovement();
        movement.setItem(item);
        movement.setFromLocation(item.getLocation());
        movement.setToLocation(destination);
        movement.setQuantity(quantity);
        movement.setMovedAt(LocalDateTime.now());
        movements.save(movement);
        item.setLocation(destination);
        return ItemResponse.from(items.save(item));
    }

    @Transactional
    public ItemResponse consume(Long id, Integer quantity, User user) {
        Item item = forUpdate(id, user);
        if (quantity == null || quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad a consumir debe ser positiva");
        }
        if (quantity > item.getQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay suficientes unidades");
        }
        item.setQuantity(item.getQuantity() - quantity);
        ItemConsumption consumption = new ItemConsumption();
        consumption.setItem(item);
        consumption.setConsumedBy(user);
        consumption.setLocation(item.getLocation());
        consumption.setQuantity(quantity);
        consumption.setRemainingQuantity(item.getQuantity());
        consumption.setConsumedAt(LocalDateTime.now());
        consumptions.save(consumption);
        return ItemResponse.from(items.save(item));
    }

    public List<ItemConsumptionResponse> consumptionHistory(Long id, User user) {
        requireAccessible(id, user);
        return consumptions.findByItemIdOrderByConsumedAtDescIdDesc(id).stream()
                .map(ItemConsumptionResponse::from).toList();
    }

    private Item forUpdate(Long id, User user) {
        Long houseId = items.findAccessibleHouseId(id, user.getId()).orElseThrow(this::notFound);
        houses.lockForUpdate(houseId, user);
        return requireAccessible(id, user);
    }

    private void requireOwner(Item item, User user) {
        if (!item.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el propietario puede modificar o borrar el objeto");
        }
    }

    private void applyDetails(Item item, ItemRequest request) {
        item.setName(request.getName().strip());
        item.setDescription(request.getDescription());
        item.setCategory(request.getCategory());
        item.setQuantity(request.getQuantity());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Objeto no encontrado");
    }
}
