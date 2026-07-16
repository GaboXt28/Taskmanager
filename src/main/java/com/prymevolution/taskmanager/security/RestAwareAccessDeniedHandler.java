package com.prymevolution.taskmanager.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Decide qué hacer cuando un usuario autenticado (con JWT válido) intenta acceder a un
 * recurso para el que no tiene el rol requerido (ej. un USER intentando entrar a /admin).
 *
 *   - "/api/**"      → 403 JSON, para clientes API
 *   - cualquier otra → redirect a la página HTML de error 403 (comportamiento previo conservado)
 */
@Component
public class RestAwareAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Acceso denegado. No tienes el rol requerido.\"}");
        } else {
            response.sendRedirect("/error/403");
        }
    }
}
