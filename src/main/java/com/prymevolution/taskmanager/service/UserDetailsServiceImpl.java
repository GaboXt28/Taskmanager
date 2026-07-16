package com.prymevolution.taskmanager.service;

import com.prymevolution.taskmanager.entity.User;
import com.prymevolution.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Puente entre nuestra base de datos de usuarios y el sistema de autenticación de Spring Security.
 *
 * ¿Por qué existe esta clase?
 * Spring Security no sabe nada de nuestra tabla "users" ni de nuestra entidad User.
 * Para autenticar usuarios, Spring Security necesita un objeto UserDetails que contenga
 * el username, el password (hash) y los roles/permisos (authorities).
 *
 * Esta clase implementa la interfaz UserDetailsService de Spring Security, que tiene
 * UN SOLO MÉTODO: loadUserByUsername(). Spring Security lo llama automáticamente
 * cada vez que alguien intenta hacer login en el formulario.
 *
 * Flujo completo de autenticación:
 *   1. Usuario envía POST /login con username y password
 *   2. Spring Security llama: loadUserByUsername(username)
 *   3. Este método busca el usuario en nuestra BD
 *   4. Si existe, construye y devuelve un UserDetails con username, hash y roles
 *   5. Spring Security compara el password ingresado con el hash usando BCrypt
 *   6. Si coincide → sesión creada, redirect a /tasks
 *   7. Si no coincide → redirect a /login?error
 *
 * @Service → registra esta clase como bean de Spring.
 * @RequiredArgsConstructor → Lombok genera el constructor para inyección de dependencias.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    /** Repositorio para buscar usuarios por username en la BD. */
    private final UserRepository userRepository;

    /**
     * Carga un usuario de la base de datos y lo convierte al formato que Spring Security entiende.
     *
     * Este método es llamado AUTOMÁTICAMENTE por Spring Security en el momento del login.
     * No se llama directamente desde ningún controlador o servicio propio.
     *
     * @Transactional → necesario porque al cargar el usuario pueden iniciarse relaciones
     *   JPA (como tareas) en modo LAZY. La transacción activa mantiene el contexto de
     *   persistencia abierto durante toda la ejecución del método.
     *
     * @param username el nombre de usuario ingresado en el formulario de login
     * @return UserDetails objeto que Spring Security usa para verificar la contraseña y roles
     * @throws UsernameNotFoundException si el username no existe en la BD.
     *         Spring Security captura esta excepción y redirige a /login?error automáticamente.
     */
    @Override
    @Transactional
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // Buscamos el usuario en nuestra tabla users
        // Si no existe, lanzamos UsernameNotFoundException que Spring Security maneja internamente
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Usuario no encontrado en la base de datos: " + username));

        // Construimos el objeto UserDetails que Spring Security necesita para autenticar.
        // Usamos el builder de la clase User de Spring Security (no nuestra entidad User).
        // Nota: aquí importamos org.springframework.security.core.userdetails.User, que es
        //       diferente de nuestra entidad com.prymevolution.taskmanager.entity.User.
        return org.springframework.security.core.userdetails.User.builder()
                // Username: el mismo que vino de nuestra BD
                .username(user.getUsername())

                // Password: el hash BCrypt almacenado en BD. Spring Security comparará
                // el password ingresado contra este hash usando BCryptPasswordEncoder.matches()
                // NUNCA comparar passwords en texto plano — siempre contra el hash.
                .password(user.getPassword())

                // Authorities (permisos/roles): lista de lo que este usuario puede hacer.
                // Spring Security espera el prefijo "ROLE_" para que hasRole("ADMIN") funcione.
                // user.getRole().name() devuelve "USER" o "ADMIN"
                // → "ROLE_USER" o "ROLE_ADMIN"
                // SimpleGrantedAuthority envuelve el String en el tipo que Spring Security espera
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))

                // Si enabled es false: la cuenta está suspendida. Spring Security
                // rechazará el login aunque la contraseña sea correcta, mostrando:
                // "User is disabled" en lugar del mensaje de credenciales incorrectas.
                .accountLocked(!user.isEnabled())
                .disabled(!user.isEnabled())

                .build();
    }
}
