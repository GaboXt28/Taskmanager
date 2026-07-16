package com.prymevolution.taskmanager.controller.api;

import com.prymevolution.taskmanager.dto.JwtResponse;
import com.prymevolution.taskmanager.dto.LoginRequest;
import com.prymevolution.taskmanager.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API REST de autenticación — punto de entrada para clientes que consumen el sistema
 * de forma programática (Postman, una SPA, pruebas automatizadas, etc.), en lugar de
 * navegar las páginas Thymeleaf.
 *
 * Diferencia con AuthController (MVC):
 *   - AuthController.login()   → recibe un formulario HTML, guarda el JWT en una cookie
 *                                 HttpOnly y redirige (pensado para el navegador).
 *   - AuthRestController.login() → recibe y devuelve JSON puro; el token viaja en el
 *                                 cuerpo de la respuesta y el cliente decide cómo guardarlo
 *                                 y cómo enviarlo de vuelta (cabecera "Authorization: Bearer <token>").
 *
 * Ambos delegan en el mismo AuthService, que es quien realmente valida credenciales
 * (vía AuthenticationManager) y genera el token (vía JwtUtil). No hay lógica de
 * autenticación duplicada entre los dos controladores.
 *
 * @RestController → combina @Controller + @ResponseBody: cada método serializa su valor
 *   de retorno directamente a JSON en el cuerpo de la respuesta, sin resolver vistas Thymeleaf.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthRestController {

    private final AuthService authService;

    /**
     * Autentica un usuario y devuelve su JWT.
     *
     * Petición esperada (Postman / curl):
     *   POST /api/auth/login
     *   Content-Type: application/json
     *   { "username": "admin", "password": "admin123" }
     *
     * Respuesta exitosa (200 OK):
     *   { "token": "eyJhbGciOiJIUzI1NiJ9...", "username": "admin", "roles": ["ROLE_ADMIN"] }
     *
     * Respuesta fallida (401 Unauthorized):
     *   { "error": "Usuario o contraseña incorrectos" }
     *
     * @param request credenciales recibidas en el cuerpo JSON
     * @return 200 con el JWT si las credenciales son válidas; 401 en caso contrario
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthService.AuthResult result = authService.login(request.getUsername(), request.getPassword());
            return ResponseEntity.ok(new JwtResponse(result.token(), result.username(), result.roles()));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Usuario o contraseña incorrectos"));
        }
    }
}
