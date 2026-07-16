package com.prymevolution.taskmanager.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * Cuerpo JSON devuelto por POST /api/auth/login cuando la autenticación es exitosa:
 *   { "token": "...", "username": "admin", "roles": ["ROLE_ADMIN"] }
 *
 * El cliente API (Postman, una SPA, etc.) debe guardar "token" y enviarlo en cada
 * petición posterior como cabecera: Authorization: Bearer <token>
 */
@Data
@AllArgsConstructor
public class JwtResponse {
    private String token;
    private String username;
    private List<String> roles;
}
