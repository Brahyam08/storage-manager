package com.brahyam.storagemanager.dto;

import com.brahyam.storagemanager.entity.Role;
import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    private String password;
    private Role role;
}