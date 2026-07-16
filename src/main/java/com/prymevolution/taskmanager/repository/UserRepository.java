package com.prymevolution.taskmanager.repository;

import com.prymevolution.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad User.
 *
 * Patrón aplicado: Repository (capa de acceso a datos del modelo MVC).
 * Extiende JpaRepository<User, Long>, lo que proporciona automáticamente todos los
 * métodos CRUD estándar (save, findById, findAll, deleteById, count, etc.) sin código adicional.
 *
 * Este repositorio tiene tres usos principales en la aplicación:
 *   1. UserDetailsServiceImpl → loadUserByUsername() para autenticar con Spring Security
 *   2. UserService → registro, consulta y eliminación de usuarios
 *   3. DataInitializer → verificar si ya existen usuarios antes de sembrar datos demo
 *
 * @Repository hace dos cosas:
 *   1. Registra la interfaz como bean de Spring (se puede inyectar con @Autowired o constructor)
 *   2. Envuelve las excepciones de persistencia en DataAccessException de Spring,
 *      lo que unifica el manejo de errores independientemente del proveedor JPA usado
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario por su nombre de usuario para el proceso de autenticación.
     *
     * SQL generado por Spring Data JPA a partir del nombre del método:
     *   SELECT * FROM users WHERE username = ?
     *
     * Devuelve Optional<User> (no User directamente) porque:
     *   - Si el usuario existe → Optional.of(user)
     *   - Si no existe → Optional.empty()
     * El llamador decide qué hacer en cada caso (.orElseThrow(), .orElse(null), etc.)
     * sin necesidad de verificar si el resultado es null, lo que evita NullPointerException.
     *
     * Usado principalmente en:
     *   - UserDetailsServiceImpl.loadUserByUsername() → carga el usuario para Spring Security
     *   - UserService.findByUsername() → para obtener la entidad completa del usuario logueado
     *
     * @param username nombre de usuario a buscar
     * @return Optional con el usuario si existe, Optional vacío si no existe
     */
    Optional<User> findByUsername(String username);

    /**
     * Verifica si ya existe un usuario con ese username — usado en el registro para
     * evitar duplicados antes de intentar guardar.
     *
     * SQL generado equivalente:
     *   SELECT COUNT(*) > 0 FROM users WHERE username = ?
     *   (o SELECT EXISTS(...) dependiendo del dialecto)
     *
     * Se prefiere existsBy sobre findBy cuando solo nos interesa saber si existe o no,
     * ya que no carga el objeto completo de la BD, siendo más eficiente.
     *
     * @param username nombre de usuario a verificar
     * @return true si ya está registrado, false si está disponible
     */
    boolean existsByUsername(String username);

    /**
     * Verifica si ya existe un usuario con ese email — mismo propósito que existsByUsername.
     *
     * SQL generado equivalente:
     *   SELECT COUNT(*) > 0 FROM users WHERE email = ?
     *
     * Se valida antes del registro para dar feedback inmediato al usuario en el formulario,
     * sin esperar a que MySQL lance una excepción de constraint UNIQUE.
     *
     * @param email correo electrónico a verificar
     * @return true si el email ya está en uso, false si está disponible
     */
    boolean existsByEmail(String email);
}
