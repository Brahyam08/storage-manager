package com.brahyam.storagemanager.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    @Size(max = 255)
    private String email;
    @NotBlank(message = "La contraseña es obligatoria")
    @Size(max = 72, message = "La contraseña no puede superar 72 caracteres")
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }
}
