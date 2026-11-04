package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.dto.*;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationContentsService {
    private final LocationService locations;
    private final ItemService itemService;
    private final ItemRepository items;
    private final ItemUseRepository uses;

    public List<ItemResponse> preview(Long id, User user) {
        Location source = locations.findActiveById(id, user);
        return checkedContents(source, user);
    }

    @Transactional
    public List<ItemResponse> move(Long id, MoveLocationContentsRequest request, User user) {
        Location source = locations.lockForUpdate(id, user);
        Location target = locations.findActiveById(request.toLocationId(), user);
        if (source.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Elige una ubicación de destino diferente");
        }
        if (!source.getHouse().getId().equals(target.getHouse().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El traslado debe hacerse dentro de la misma casa");
        }
        List<ItemResponse> current = checkedContents(source, user);
        Map<Long, Integer> expected = new HashMap<>();
        for (MoveLocationContentsRequest.ItemSnapshot snapshot : request.items()) {
            if (expected.putIfAbsent(snapshot.id(), snapshot.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La lista contiene objetos repetidos");
            }
        }
        if (expected.size() != current.size() || current.stream().anyMatch(
                item -> !Objects.equals(expected.get(item.id()), item.quantity()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "El contenido ha cambiado. Actualiza el resumen antes de confirmar el traslado");
        }
        List<ItemResponse> moved = new ArrayList<>();
        for (ItemResponse item : current) {
            moved.add(itemService.move(item.id(), target.getId(), item.quantity(), user));
        }
        return moved;
    }

    private List<ItemResponse> checkedContents(Location source, User user) {
        List<Item> visible = items.findAccessibleWithFilters(user.getId(), source.getHouse().getId(),
                null, null, source.getId()).stream().filter(item -> !item.isArchived()).toList();
        if (visible.size() != items.countByLocationIdAndArchivedFalse(source.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No es posible completar este traslado conjunto con tus permisos. Revisa los objetos de forma individual");
        }
        if (visible.size() > 500) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Hay demasiados registros para un traslado conjunto. Reorganízalos por partes");
        }
        List<ItemResponse> result = new ArrayList<>();
        for (Item item : visible) {
            int inUse = Math.toIntExact(uses.activeQuantity(item.getId()));
            if (inUse > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Devuelve las piezas en uso antes de trasladar el contenido");
            }
            result.add(ItemResponse.from(item, inUse));
        }
        return result;
    }
}
