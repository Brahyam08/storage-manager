package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.dto.ItemRequest;
import com.brahyam.storagemanager.entity.Item;
import com.brahyam.storagemanager.entity.Location;
import com.brahyam.storagemanager.service.ItemMovementService;
import com.brahyam.storagemanager.service.ItemService;
import com.brahyam.storagemanager.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;
    private final ItemMovementService itemMovementService;
    private final LocationService locationService;

    @GetMapping
    public ResponseEntity<List<Item>> findAll(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long locationId) {
        return ResponseEntity.ok(itemService.findWithFilters(search, category, locationId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Item> findById(@PathVariable Long id) {
        return ResponseEntity.ok(itemService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Item> save(@Valid @RequestBody ItemRequest request) {
        Location location = locationService.findById(request.getLocationId());
        Item item = new Item();
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setCategory(request.getCategory());
        item.setQuantity(request.getQuantity());
        item.setLocation(location);
        return ResponseEntity.status(HttpStatus.CREATED).body(itemService.save(item));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Item> update(@PathVariable Long id, @Valid @RequestBody ItemRequest request) {
        Location location = locationService.findById(request.getLocationId());
        Item item = new Item();
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setCategory(request.getCategory());
        item.setQuantity(request.getQuantity());
        item.setLocation(location);
        return ResponseEntity.ok(itemService.update(id, item));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id) {
        itemService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/move")
    public ResponseEntity<Item> move(@PathVariable Long id,
                                     @RequestParam Long toLocationId,
                                     @RequestParam Integer quantity) {
        return ResponseEntity.ok(itemService.move(id, toLocationId, quantity));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<?> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(itemMovementService.findByItemId(id));
    }

}