package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.dto.UserResponse;
import com.brahyam.storagemanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public UserResponse currentUser(Principal principal) {
        return UserResponse.from(userService.findByEmail(principal.getName()));
    }

    @GetMapping("/{id}")
    public UserResponse findById(@PathVariable Long id, Principal principal) {
        return UserResponse.from(userService.findOwnById(id, principal.getName()));
    }
}
