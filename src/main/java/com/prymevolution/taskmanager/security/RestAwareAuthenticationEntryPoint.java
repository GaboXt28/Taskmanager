package com.prymevolution.taskmanager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Decide qué hacer cuando una petición sin autenticar (o con un JWT inválido/expirado)
 * intenta acceder a una ruta protegida.
 *
 * En una API puramente stateless esto normalmente devolvería siempre 401 Unauthorized.
 * Como esta aplicación también sirve páginas Thymeleaf con navegación de navegador,
 * distinguimos por prefijo de ruta:
 *   - "/api/**"      → cliente API (Postman, fetch, etc.) → 401 JSON, sin redirects
 *   - cualquier otra → navegador → redirect a /login para que el usuario inicie sesión
 */
@Component
public class RestAwareAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                          AuthenticationException authException) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"No autenticado. Token JWT ausente o inválido.\"}");
        } else {
            response.sendRedirect("/login");
        }
    }
}
