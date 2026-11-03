package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.dto.*;
import com.brahyam.storagemanager.entity.*;
import com.brahyam.storagemanager.repository.ItemUseRepository;
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
public class ItemUseService {
    private final ItemService items;
    private final ItemUseRepository uses;

    @Transactional
    public ItemUseResponse take(Long itemId, int quantity, User user) {
        Item item = items.forUpdate(itemId, user);
        if (item.getKind() != ItemKind.REUSABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Solo los reutilizables se retiran para usar");
        }
        if (quantity < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La cantidad debe ser positiva");
        }
        long available = item.getQuantity() - uses.activeQuantity(itemId);
        if (quantity > available) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No hay suficientes objetos disponibles");
        }
        ItemUse use = new ItemUse();
        use.setItem(item);
        use.setUsedBy(user);
        use.setLocation(item.getLocation());
        use.setQuantity(quantity);
        use.setTakenAt(LocalDateTime.now());
        return ItemUseResponse.from(uses.save(use));
    }

    @Transactional
    public ItemUseResponse giveBack(Long itemId, Long useId, User user) {
        Item item = items.forUpdate(itemId, user);
        ItemUse use = uses.findByIdAndItemId(useId, itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Uso no encontrado"));
        if (!use.getUsedBy().getId().equals(user.getId()) && !item.getOwner().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo quien lo retiró o su propietario puede registrar la devolución");
        }
        if (use.getReturnedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La devolución ya está registrada");
        }
        use.setReturnedAt(LocalDateTime.now());
        return ItemUseResponse.from(uses.save(use));
    }

    public List<ItemUseResponse> history(Long itemId, User user) {
        items.requireAccessible(itemId, user);
        return uses.findByItemIdOrderByTakenAtDescIdDesc(itemId).stream().map(ItemUseResponse::from).toList();
    }
}
