package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.dto.HouseRequest;
import com.brahyam.storagemanager.dto.JoinHouseRequest;
import com.brahyam.storagemanager.dto.HouseInvitationResponse;
import com.brahyam.storagemanager.service.HouseMembershipService;
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
    private final HouseMembershipService memberships;

    @PostMapping
    public ResponseEntity<HouseResponse> create(@Valid @RequestBody HouseRequest request,
                                               Principal principal) {
        User owner = userService.findByEmail(principal.getName());
        HouseResponse house = HouseResponse.from(houseService.create(request.name(), owner));
        return ResponseEntity.created(URI.create("/api/houses/" + house.id())).body(house);
    }

    @PutMapping("/{id}")
    public HouseResponse rename(@PathVariable Long id, @Valid @RequestBody HouseRequest request,
                                Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return HouseResponse.from(houseService.rename(id, request.name(), user));
    }

    @PostMapping("/{id}/invitation")
    public HouseInvitationResponse renewInvitation(@PathVariable Long id, Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return new HouseInvitationResponse(memberships.renewInvitation(id, user));
    }

    @PostMapping("/join")
    public HouseResponse join(@Valid @RequestBody JoinHouseRequest request, Principal principal) {
        User user = userService.findByEmail(principal.getName());
        return HouseResponse.from(memberships.join(request.code(), user));
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
