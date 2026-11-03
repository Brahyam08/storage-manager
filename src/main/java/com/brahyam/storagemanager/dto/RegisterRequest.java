package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(max = 255)
    private String username;
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 255)
    private String email;
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.strip();
    }
}
