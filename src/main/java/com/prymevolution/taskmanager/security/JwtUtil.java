package com.prymevolution.taskmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilidad central para la generación y validación de JSON Web Tokens (JWT).
 *
 * Un JWT tiene tres partes separadas por puntos: Header.Payload.Signature
 *   - Header    → algoritmo de firma usado (HS256) y tipo de token (JWT). Lo genera la librería JJWT.
 *   - Payload   → los "claims": subject (username), authorities (roles), fecha de emisión (iat)
 *                 y fecha de expiración (exp). Van codificados en Base64Url, NO encriptados:
 *                 cualquiera puede leerlos, pero no puede modificarlos sin invalidar la firma.
 *   - Signature → HMAC-SHA256 del header+payload firmado con la clave secreta (jwt.secret).
 *                 Garantiza integridad: si el token se altera, la firma no coincide y se rechaza.
 *
 * Esta clase es el único lugar de la aplicación que conoce la clave secreta y el algoritmo.
 * Tanto el login (emisión) como el filtro de autenticación (validación) dependen de ella.
 */
@Component
public class JwtUtil {

    /** Clave secreta usada para firmar y verificar los tokens (Base64, ver application.properties). */
    private final SecretKey secretKey;

    /** Tiempo de vida del token en milisegundos. */
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration}") long expirationMs) {
        // Keys.hmacShaKeyFor exige una clave de al menos 256 bits para HS256
        this.secretKey = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
    }

    /**
     * Genera un nuevo JWT firmado para un usuario autenticado.
     *
     * El "subject" del token es el username, y se agrega un claim personalizado "roles"
     * con las autoridades del usuario (ej. "ROLE_ADMIN") para que el filtro pueda reconstruir
     * el objeto Authentication sin volver a consultar la base de datos en cada petición.
     *
     * @param userDetails usuario ya autenticado por el AuthenticationManager
     * @return el token JWT completo (Header.Payload.Signature) como String
     */
    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    /** Extrae el username (subject) del token. Lanza JwtException si el token es inválido. */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extrae los roles almacenados en el claim personalizado "roles". */
    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return parseClaims(token).get("roles", List.class);
    }

    /**
     * Valida que el token esté correctamente firmado, no haya expirado y pertenezca al usuario dado.
     *
     * @param token       el JWT recibido en la petición
     * @param userDetails el usuario cargado desde la base de datos con ese username
     * @return true si el token es válido para ese usuario
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    /**
     * Parsea y verifica la firma del token. Si la firma no coincide con la clave secreta,
     * o el token está corrupto/expirado, JJWT lanza una excepción (JwtException) que
     * el llamador debe manejar en lugar de asumir que el token es confiable.
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Método de conveniencia para detectar tokens expirados sin propagar la excepción de JJWT. */
    public boolean isTokenExpiredSafe(String token) {
        try {
            return isExpired(token);
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
}
