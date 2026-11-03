package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.dto.HouseRequest;
import com.brahyam.storagemanager.dto.HouseResponse;
import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.service.HouseService;
import com.brahyam.storagemanager.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/houses")
@RequiredArgsConstructor
public class HouseController {
    private final HouseService houseService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<HouseResponse> create(@Valid @RequestBody HouseRequest request,
                                               Principal principal) {
        User owner = userService.findByEmail(principal.getName());
        HouseResponse house = HouseResponse.from(houseService.create(request.name(), owner));
        return ResponseEntity.created(URI.create("/api/houses/" + house.id())).body(house);
    }

    @GetMapping("/{id}")
    public HouseResponse findById(@PathVariable Long id, Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return HouseResponse.from(houseService.findById(id, user));
    }

    @GetMapping
    public List<HouseResponse> findAll(Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return houseService.findAll(user).stream().map(HouseResponse::from).toList();
    }
}
