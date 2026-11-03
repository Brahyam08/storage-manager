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
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {
    private final LocationService locations;
    private final UserService users;

    @GetMapping
    public List<LocationResponse> findAll(@RequestParam Long houseId, Principal principal) {
        return locations.findAll(houseId, users.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}")
    public LocationResponse findById(@PathVariable Long id, Principal principal) {
        return locations.findResponse(id, users.findByEmail(principal.getName()));
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody LocationRequest request, Principal principal) {
        LocationResponse location = locations.create(request, users.findByEmail(principal.getName()));
        return ResponseEntity.created(URI.create("/api/locations/" + location.id())).body(location);
    }

    @PutMapping("/{id}")
    public LocationResponse update(@PathVariable Long id, @Valid @RequestBody LocationRequest request,
                                   Principal principal) {
        return locations.update(id, request, users.findByEmail(principal.getName()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(@PathVariable Long id, Principal principal) {
        locations.deleteById(id, users.findByEmail(principal.getName()));
        return ResponseEntity.noContent().build();
    }
}
