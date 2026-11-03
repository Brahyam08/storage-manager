package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.dto.ItemMovementResponse;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.repository.ItemMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemMovementService {
    private final ItemMovementRepository movements;
    private final ItemService items;

    public List<ItemMovementResponse> findByItemId(Long itemId, User user) {
        items.requireAccessible(itemId, user);
        return movements.findByItemIdOrderByMovedAtDescIdDesc(itemId).stream()
                .map(ItemMovementResponse::from).toList();
    }
}
