package com.prymevolution.taskmanager.security;

import com.prymevolution.taskmanager.service.UserDetailsServiceImpl;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro que intercepta TODAS las peticiones HTTP para implementar autenticación sin estado (stateless).
 *
 * Se ejecuta UNA vez por petición (OncePerRequestFilter) y ANTES del filtro estándar de
 * Spring Security (UsernamePasswordAuthenticationFilter), como se configura en SecurityConfig.
 *
 * Flujo de este filtro en cada request:
 *   1. Busca el token JWT: primero en la cabecera "Authorization: Bearer <token>" (uso típico
 *      de un cliente API/Postman), y si no está, en la cookie "jwt" (uso típico del navegador,
 *      que la envía automáticamente en cada petición sin necesidad de JavaScript).
 *   2. Si no hay token, deja pasar la petición sin autenticar (SecurityConfig decide si la ruta
 *      requiere autenticación; si la requiere, el AuthenticationEntryPoint se encargará del rechazo).
 *   3. Si hay token, extrae el username y valida la firma/expiración con JwtUtil.
 *   4. Si es válido, carga el UserDetails desde la BD y construye un Authentication que se
 *      guarda en el SecurityContextHolder — así el resto del pipeline (incluyendo @PreAuthorize
 *      y sec:authorize en Thymeleaf) ve al usuario como autenticado para ESTA petición.
 *
 * Como no se usa HttpSession, el SecurityContextHolder se reconstruye desde cero en cada
 * petición a partir del token: el servidor no "recuerda" nada entre peticiones.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_COOKIE_NAME = "jwt";

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        // Si no hay token o ya hay una autenticación en el contexto, continuamos sin tocar nada
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String username = jwtUtil.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Este es el paso que "autentica" la petición actual ante el resto de Spring Security
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // Token corrupto, mal firmado o expirado: simplemente no autenticamos.
                // No lanzamos la excepción; dejamos que SecurityConfig decida qué hacer con
                // una petición no autenticada a una ruta protegida (401/redirect vía entry point).
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Busca el token primero en la cabecera Authorization y, si no está, en la cookie "jwt". */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (JWT_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
