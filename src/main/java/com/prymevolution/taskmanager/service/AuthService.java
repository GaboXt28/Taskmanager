package com.prymevolution.taskmanager.service;

import com.prymevolution.taskmanager.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Punto único de autenticación de la aplicación: valida credenciales y emite el JWT resultante.
 *
 * Tanto el login "de navegador" (AuthController, que guarda el token en una cookie) como el
 * login "de API" (AuthRestController, que devuelve el token en el cuerpo JSON) delegan aquí
 * para no duplicar la lógica de autenticación.
 *
 * @Service → capa de negocio de Spring.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * Bean estándar de Spring Security que orquesta la autenticación: delega en el
     * AuthenticationProvider configurado (DaoAuthenticationProvider en SecurityConfig),
     * el cual usa UserDetailsServiceImpl + BCryptPasswordEncoder para verificar la contraseña.
     */
    private final AuthenticationManager authenticationManager;

    private final JwtUtil jwtUtil;

    /**
     * Autentica al usuario y, si las credenciales son correctas, genera su JWT.
     *
     * @param username nombre de usuario ingresado
     * @param password contraseña en texto plano ingresada
     * @return el resultado con el token y los datos básicos del usuario
     * @throws org.springframework.security.core.AuthenticationException si las credenciales son inválidas
     *         (BadCredentialsException) o la cuenta está deshabilitada (DisabledException).
     *         Spring Security la lanza dentro de authenticationManager.authenticate(); el llamador
     *         (AuthController o AuthRestController) decide cómo comunicar el fallo al cliente.
     */
    public AuthResult login(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails);

        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return new AuthResult(token, userDetails.getUsername(), roles);
    }

    /** Resultado inmutable de un login exitoso: token emitido + datos básicos del usuario. */
    public record AuthResult(String token, String username, List<String> roles) {
    }
}
