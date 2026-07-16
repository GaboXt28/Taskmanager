package com.prymevolution.taskmanager.service;

import com.prymevolution.taskmanager.entity.User;
import com.prymevolution.taskmanager.entity.UserRole;
import com.prymevolution.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de lógica de negocio para la gestión de usuarios.
 *
 * Patrón aplicado: Service (capa de lógica de negocio del modelo MVC).
 *
 * Responsabilidades de este service:
 *   - Registrar nuevos usuarios (encriptando la contraseña y asignando rol por defecto)
 *   - Proveer métodos de consulta que usan otros servicios y controladores
 *   - Delegar la persistencia al UserRepository
 *
 * Separación de responsabilidades:
 *   AuthController recibe los datos del formulario de registro y delega a UserService.register().
 *   UserService aplica las reglas: encriptar contraseña, asignar rol USER, activar la cuenta.
 *   UserRepository persiste el objeto en MySQL.
 *
 * @Service → registra la clase como bean de Spring en la capa de servicio.
 * @RequiredArgsConstructor → Lombok genera el constructor para inyección de dependencias.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** Repositorio para acceder a la tabla users en MySQL. */
    private final UserRepository userRepository;

    /**
     * PasswordEncoder inyectado desde SecurityConfig.
     * Es el bean BCryptPasswordEncoder definido allí.
     * Se inyecta aquí (no se crea aquí) para seguir el principio de Inversión de Dependencias:
     * UserService no decide qué algoritmo de hash usar; eso lo decide la configuración.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Registra un nuevo usuario en el sistema aplicando las reglas de seguridad.
     *
     * Reglas aplicadas en este método:
     *   1. La contraseña NUNCA se guarda en texto plano — se convierte a hash BCrypt.
     *      BCrypt es un algoritmo de hash adaptativo: es computacionalmente costoso,
     *      lo que hace que los ataques de fuerza bruta sean extremadamente lentos.
     *      Cada hash es único (incluye un "salt" aleatorio) así que dos usuarios con la
     *      misma contraseña tienen hashes distintos.
     *
     *   2. Todo usuario registrado por formulario recibe el rol USER.
     *      Nadie puede registrarse como ADMIN desde la web — el admin se crea en DataInitializer.
     *
     *   3. La cuenta se activa inmediatamente (enabled = true).
     *
     * @Transactional → si el save() falla (ej. username duplicado por race condition),
     *   el sistema hace rollback y no queda ningún dato a medias.
     *
     * @param user objeto User con los datos del formulario (contraseña aún en texto plano)
     * @return el User guardado en la BD, ahora con id asignado y contraseña encriptada
     */
    @Transactional
    public User register(User user) {
        // passwordEncoder.encode() transforma "miPassword123" en "$2a$10$xyz..." (BCrypt)
        // CRÍTICO: encode() siempre produce un hash diferente por el salt aleatorio,
        // pero passwordEncoder.matches("miPassword123", hash) siempre devuelve true
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // Forzamos USER sin importar lo que el usuario haya enviado en la petición HTTP
        // Esto protege contra ataques de manipulación de parámetros (mass assignment)
        user.setRole(UserRole.USER);

        user.setEnabled(true);
        return userRepository.save(user);
    }

    /**
     * Busca un usuario por username y lo devuelve envuelto en Optional.
     *
     * Optional<User> comunica al llamador que el usuario puede no existir.
     * Ejemplo de uso en TaskController:
     *   userService.findByUsername(ud.getUsername()).orElseThrow(() -> new RuntimeException(...))
     *
     * @param username nombre de usuario a buscar
     * @return Optional con el User si existe, vacío si no
     */
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Verifica si un username ya está registrado — para la validación del formulario de registro.
     * Más eficiente que findByUsername() porque no carga el objeto User completo.
     *
     * @param username nombre de usuario a verificar
     * @return true si ya existe, false si está disponible
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Verifica si un email ya está registrado — para la validación del formulario de registro.
     *
     * @param email correo electrónico a verificar
     * @return true si ya está en uso, false si está disponible
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Devuelve todos los usuarios del sistema.
     * Solo el administrador necesita esta información (se controla en AdminController).
     *
     * @return lista con todos los usuarios registrados
     */
    public List<User> findAll() {
        return userRepository.findAll();
    }

    /**
     * Elimina un usuario y, por cascada, todas sus tareas.
     *
     * La eliminación en cascada está definida en User.java:
     *   @OneToMany(cascade = CascadeType.ALL)
     * Hibernate traduce esto a: DELETE FROM tasks WHERE user_id = ? (primero las tareas)
     * y luego: DELETE FROM users WHERE id = ?
     *
     * @Transactional → garantiza que si falla alguna de las eliminaciones en cascada,
     *   toda la operación se revierte y no quedan datos huérfanos.
     *
     * @param id clave primaria del usuario a eliminar
     */
    @Transactional
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    /**
     * Cuenta el total de usuarios registrados en el sistema.
     * Usado en el dashboard del administrador para mostrar la métrica de usuarios.
     *
     * @return número total de usuarios en la BD
     */
    public long countAll() {
        return userRepository.count();
    }
}
