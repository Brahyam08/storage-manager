package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.Role;
import com.brahyam.storagemanager.entity.User;

public record UserResponse(Long id, String username, String email, Role role) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getRole());
    }
}
