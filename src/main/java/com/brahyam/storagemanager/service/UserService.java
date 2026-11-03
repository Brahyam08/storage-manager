package com.brahyam.storagemanager.service;

import com.brahyam.storagemanager.entity.User;
import com.brahyam.storagemanager.entity.Role;
import com.brahyam.storagemanager.dto.RegisterRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;
import com.brahyam.storagemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterRequest request) {
        String email = request.getEmail().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(request.getUsername()) || userRepository.existsByEmailIgnoreCase(email)) {
            throw registrationConflict();
        }
        User user = new User(null, request.getUsername(), email,
                passwordEncoder.encode(request.getPassword()), Role.VIEWER);
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // The database remains authoritative if registrations race.
            throw registrationConflict();
        }
    }

    private ResponseStatusException registrationConflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "No se puede registrar esa cuenta");
    }

    public User findOwnById(Long id, String authenticatedEmail) {
        return userRepository.findByIdAndEmail(id, authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

}
