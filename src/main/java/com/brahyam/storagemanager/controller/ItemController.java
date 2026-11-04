package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.dto.*;
import com.brahyam.storagemanager.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {
    private final ItemService items;
    private final ItemUseService uses;
    private final ItemMovementService movements;
    private final UserService users;

    @GetMapping
    public List<ItemResponse> findAll(@RequestParam(required = false) Long houseId,
            @RequestParam(required = false) String search, @RequestParam(required = false) String category,
            @RequestParam(required = false) Long locationId, @RequestParam(defaultValue = "false") boolean includeArchived, Principal principal) {
        return items.findWithFilters(houseId, search, category, locationId, includeArchived, users.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}")
    public ItemResponse findById(@PathVariable Long id, Principal principal) {
        return items.findById(id, users.findByEmail(principal.getName()));
    }

    @PostMapping
    public ResponseEntity<ItemResponse> create(@Valid @RequestBody ItemRequest request, Principal principal) {
        ItemResponse item = items.create(request, users.findByEmail(principal.getName()));
        return ResponseEntity.created(URI.create("/api/items/" + item.id())).body(item);
    }

    @PutMapping("/{id}")
    public ItemResponse update(@PathVariable Long id, @Valid @RequestBody ItemRequest request, Principal principal) {
        return items.update(id, request, users.findByEmail(principal.getName()));
    }

    @PatchMapping("/{id}/archive")
    public ItemResponse archive(@PathVariable Long id, Principal principal) {
        return items.archive(id, users.findByEmail(principal.getName()));
    }

    @PatchMapping("/{id}/restore")
    public ItemResponse restore(@PathVariable Long id, Principal principal) {
        return items.restore(id, users.findByEmail(principal.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id, Principal principal) {
        items.deleteById(id, users.findByEmail(principal.getName()));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/move")
    public ItemResponse move(@PathVariable Long id, @RequestParam Long toLocationId,
                            @RequestParam Integer quantity, Principal principal) {
        return items.move(id, toLocationId, quantity, users.findByEmail(principal.getName()));
    }

    @PostMapping("/{id}/consume")
    public ItemResponse consume(@PathVariable Long id, @Valid @RequestBody ConsumeItemRequest request,
                                Principal principal) {
        return items.consume(id, request.quantity(), users.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}/consumptions")
    public List<ItemConsumptionResponse> consumptions(@PathVariable Long id, Principal principal) {
        return items.consumptionHistory(id, users.findByEmail(principal.getName()));
    }

    @PostMapping("/{id}/restocks")
    public ItemResponse restock(@PathVariable Long id, @Valid @RequestBody RestockItemRequest request,
                               Principal principal) {
        return items.restock(id, request.quantity(), users.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}/restocks")
    public List<ItemRestockResponse> restocks(@PathVariable Long id, Principal principal) {
        return items.restockHistory(id, users.findByEmail(principal.getName()));
    }

    @PostMapping("/{id}/uses")
    public ResponseEntity<ItemUseResponse> take(@PathVariable Long id, @Valid @RequestBody UseItemRequest request, Principal principal) {
        ItemUseResponse use = uses.take(id, request.quantity(), users.findByEmail(principal.getName()));
        return ResponseEntity.created(URI.create("/api/items/" + id + "/uses/" + use.id())).body(use);
    }

    @PostMapping("/{id}/uses/{useId}/return")
    public ItemUseResponse giveBack(@PathVariable Long id, @PathVariable Long useId, Principal principal) {
        return uses.giveBack(id, useId, users.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}/uses")
    public List<ItemUseResponse> useHistory(@PathVariable Long id, Principal principal) {
        return uses.history(id, users.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}/history")
    public List<ItemMovementResponse> history(@PathVariable Long id, Principal principal) {
        return movements.findByItemId(id, users.findByEmail(principal.getName()));
    }
}
