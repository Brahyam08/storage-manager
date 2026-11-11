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
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository items;
    private final ItemUseRepository uses;
    private final ItemMovementRepository movements;
    private final ItemConsumptionRepository consumptions;
    private final ItemRestockRepository restocks;
    private final LocationService locations;
    private final HouseService houses;

    public ItemResponse findById(Long id, User user) {
        return response(requireAccessible(id, user));
    }

    Item requireAccessible(Long id, User user) {
        return items.findAccessibleById(id, user.getId()).orElseThrow(this::notFound);
    }

    public List<ItemResponse> findWithFilters(Long houseId, String search, String category, Long locationId, User user) {
        return findWithFilters(houseId, search, category, locationId, false, user);
    }

    public List<ItemResponse> findWithFilters(Long houseId, String search, String category, Long locationId, boolean includeArchived, User user) {
        if (houseId != null) houses.findById(houseId, user);
        if (locationId != null) locations.findById(locationId, user);
        return items.findAccessibleWithFilters(user.getId(), houseId, search, category, locationId).stream()
                .filter(item -> includeArchived || !item.isArchived()).map(this::response).toList();
    }

    @Transactional
    public ItemResponse create(ItemRequest request, User user) {
        if (request.getLocationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige una ubicación al crear el objeto");
        }
        Location location = locations.lockForUpdate(request.getLocationId(), user);
        Item item = new Item();
        item.setOwner(user);
        item.setLocation(location);
        item.setHouse(location.getHouse());
        item.setVisibility(request.getVisibility() == null ? ItemVisibility.PRIVATE : request.getVisibility());
        applyKind(item, request);
        applyDetails(item, request);
        return response(items.save(item));
    }

    @Transactional
    public ItemResponse update(Long id, ItemRequest request, User user) {
        Item item = forUpdate(id, user);
        requireOwner(item, user);
        if (!java.util.Objects.equals(item.getLocation() == null ? null : item.getLocation().getId(), request.getLocationId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usa el movimiento para cambiar la ubicación");
        }
        if (request.getKind() != null && request.getKind() != item.getKind() && restocks.existsByItemId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No se puede cambiar el tipo de un objeto con reposiciones registradas");
        }
        long inUse = uses.activeQuantity(id);
        if (inUse > 0 && (request.getQuantity() < inUse
                || (request.getKind() != null && request.getKind() != item.getKind())
                || (request.getVisibility() != null && request.getVisibility() != item.getVisibility()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hay objetos en uso: no puedes reducirlos por debajo de los retirados ni cambiar su tipo o visibilidad");
        }
        applyKind(item, request);
        applyDetails(item, request);
        if (request.getVisibility() != null) item.setVisibility(request.getVisibility());
        return response(items.save(item));
    }

    @Transactional
    public void deleteById(Long id, User user) {
        Item item = lockAccessible(id, user);
        requireOwner(item, user);
        if (uses.activeQuantity(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Devuelve los objetos en uso antes de eliminarlos");
        }
        movements.deleteByItemId(id);
        consumptions.deleteByItemId(id);
        restocks.deleteByItemId(id);
        uses.deleteByItemId(id);
        items.delete(item);
        items.flush();
    }

    @Transactional
    public ItemResponse move(Long id, Long toLocationId, Integer quantity, User user) {
        Item item = forUpdate(id, user);
        if (uses.activeQuantity(id) > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Devuelve los objetos en uso antes de trasladarlos");
        if (quantity == null || quantity < 0 || !quantity.equals(item.getQuantity())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Se debe mover la cantidad completa del objeto");
        }
        Location destination = locations.findActiveById(toLocationId, user);
        if (!item.getHouse().getId().equals(destination.getHouse().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pueden mover objetos entre casas");
        }
        if (item.getLocation() != null && item.getLocation().getId().equals(destination.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El objeto ya está en esa ubicación");
        }
        ItemMovement movement = new ItemMovement();
        movement.setItem(item);
        movement.setFromLocation(item.getLocation());
        movement.setToLocation(destination);
        movement.setQuantity(quantity);
        movement.setMovedAt(LocalDateTime.now(ZoneOffset.UTC));
        movements.save(movement);
        item.setLocation(destination);
        return response(items.save(item));
    }

    @Transactional
    public ItemResponse consume(Long id, Integer quantity, User user) {
        Item item = forUpdate(id, user);
        if (item.getKind() == ItemKind.REUSABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Los objetos reutilizables no se consumen");
        }
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
        consumption.setConsumedAt(LocalDateTime.now(ZoneOffset.UTC));
        consumptions.save(consumption);
        return response(items.save(item));
    }

    @Transactional
    public ItemResponse restock(Long id, Integer quantity, User user) {
        Item item = forUpdate(id, user);
        requireOwner(item, user);
        if (item.isIndividual()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Es un objeto individual. Registra otra pieza como un objeto distinto");
        }
        if (quantity == null || quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Añade al menos una unidad");
        }
        long resultingQuantity = (long) item.getQuantity() + quantity;
        if (resultingQuantity > Integer.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cantidad resultante supera el límite admitido. Añade menos unidades");
        }
        item.setQuantity((int) resultingQuantity);
        ItemRestock restock = new ItemRestock();
        restock.setItem(item);
        restock.setRestockedBy(user);
        restock.setLocation(item.getLocation());
        restock.setQuantity(quantity);
        restock.setResultingQuantity(item.getQuantity());
        restock.setRestockedAt(LocalDateTime.now(ZoneOffset.UTC));
        restocks.save(restock);
        return response(items.save(item));
    }

    public List<ItemRestockResponse> restockHistory(Long id, User user) {
        requireAccessible(id, user);
        return restocks.findByItemIdOrderByRestockedAtDescIdDesc(id).stream()
                .map(ItemRestockResponse::from).toList();
    }

    public List<ItemConsumptionResponse> consumptionHistory(Long id, User user) {
        requireAccessible(id, user);
        return consumptions.findByItemIdOrderByConsumedAtDescIdDesc(id).stream()
                .map(ItemConsumptionResponse::from).toList();
    }

    Item forUpdate(Long id, User user) {
        Item item = lockAccessible(id, user);
        if (item.isArchived()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El objeto está archivado. Restáuralo antes de modificarlo o usarlo");
        }
        return item;
    }

    @Transactional
    public ItemResponse archive(Long id, User user) {
        Item item = lockAccessible(id, user);
        requireOwner(item, user);
        if (uses.activeQuantity(id) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Devuelve los objetos en uso antes de archivar");
        }
        item.setArchived(true);
        return response(items.save(item));
    }

    @Transactional
    public ItemResponse restore(Long id, User user) {
        Item item = lockAccessible(id, user);
        requireOwner(item, user);
        if (item.getLocation() != null) locations.findActiveById(item.getLocation().getId(), user);
        item.setArchived(false);
        return response(items.save(item));
    }

    private Item lockAccessible(Long id, User user) {
        Long houseId = items.findAccessibleHouseId(id, user.getId()).orElseThrow(this::notFound);
        houses.lockForUpdate(houseId, user);
        return requireAccessible(id, user);
    }

    private void requireOwner(Item item, User user) {
        if (!item.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el propietario puede modificar o borrar el objeto");
        }
    }

    private ItemResponse response(Item item) {
        return ItemResponse.from(item, Math.toIntExact(uses.activeQuantity(item.getId())));
    }

    private void applyKind(Item item, ItemRequest request) {
        ItemKind kind = request.getKind() == null ? item.getKind() : request.getKind();
        if (kind == ItemKind.REUSABLE && request.getQuantity() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un objeto reutilizable debe tener al menos una unidad");
        }
        boolean individual = request.getIndividual() == null ? item.isIndividual() : request.getIndividual();
        if (kind != ItemKind.REUSABLE) {
            if (Boolean.TRUE.equals(request.getIndividual())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo los reutilizables pueden ser individuales");
            }
            individual = false;
        }
        if (individual && request.getQuantity() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un objeto individual representa una sola pieza");
        }
        if (individual && !item.isIndividual() && item.getId() != null && item.getQuantity() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El grupo debe tener una sola pieza antes de marcarlo como individual");
        }
        item.setIndividual(individual);
        item.setKind(kind);
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
