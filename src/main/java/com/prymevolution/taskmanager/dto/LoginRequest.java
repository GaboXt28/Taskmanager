package com.prymevolution.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Cuerpo JSON esperado por POST /api/auth/login:
 *   { "username": "admin", "password": "admin123" }
 */
@Data
public class LoginRequest {

    @NotBlank(message = "El nombre de usuario es obligatorio")
    private String username;

    @NotBlank(message = "La contraseña es obligatoria")
    private String password;
}
