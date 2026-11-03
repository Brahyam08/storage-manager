package com.brahyam.storagemanager.controller;

import com.brahyam.storagemanager.config.JwtService;
import com.brahyam.storagemanager.dto.*;
import com.brahyam.storagemanager.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;

@RestController
@SecurityRequirements
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        requireBcryptLength(request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(userService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        requireBcryptLength(request.getPassword());
        var authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        return ResponseEntity.ok(new AuthResponse(jwtService.generateToken(authentication.getName())));
    }

    private void requireBcryptLength(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La contraseña no puede superar 72 bytes UTF-8");
        }
    }
}
