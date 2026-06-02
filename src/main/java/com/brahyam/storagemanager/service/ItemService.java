package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.Item;
import com.brahyam.storagemanager.entity.ItemMovement;
import com.brahyam.storagemanager.entity.Location;
import com.brahyam.storagemanager.repository.ItemMovementRepository;
import com.brahyam.storagemanager.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;
    private final ItemMovementRepository itemMovementRepository;
    private final LocationService locationService;

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Item findById(Long id) {
        return itemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item no encontrado"));
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public Item update(Long id, Item updatedItem) {
        Item existing = findById(id);
        existing.setName(updatedItem.getName());
        existing.setDescription(updatedItem.getDescription());
        existing.setCategory(updatedItem.getCategory());
        existing.setQuantity(updatedItem.getQuantity());
        existing.setLocation(updatedItem.getLocation());
        return itemRepository.save(existing);
    }

    public void deleteById(Long id) {
        findById(id);
        itemRepository.deleteById(id);
    }

    public Item move(Long itemId, Long toLocationId, Integer quantity) {
        Item item = findById(itemId);
        Location fromLocation = item.getLocation();
        Location toLocation = locationService.findById(toLocationId);

        ItemMovement movement = new ItemMovement();
        movement.setItem(item);
        movement.setFromLocation(fromLocation);
        movement.setToLocation(toLocation);
        movement.setQuantity(quantity);
        movement.setMovedAt(LocalDateTime.now());

        itemMovementRepository.save(movement);

        item.setLocation(toLocation);
        return itemRepository.save(item);
    }

    public List<Item> findWithFilters(String search, String category, Long locationId) {
        return itemRepository.findWithFilters(search, category, locationId);
    }
}