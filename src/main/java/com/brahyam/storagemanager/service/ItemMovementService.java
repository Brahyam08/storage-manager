package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.ItemMovement;
import com.brahyam.storagemanager.repository.ItemMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemMovementService {

    private final ItemMovementRepository itemMovementRepository;

    public List<ItemMovement> findByItemId(Long itemId) {
        return itemMovementRepository.findByItemId(itemId);
    }

}