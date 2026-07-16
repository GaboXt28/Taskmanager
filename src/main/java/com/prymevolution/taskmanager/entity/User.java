package com.prymevolution.taskmanager.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Entidad JPA que representa a un usuario registrado en el sistema.
 *
 * Patrón aplicado: Entity (capa de dominio del modelo MVC).
 * Esta clase mapea directamente la tabla "users" en MySQL.
 *
 * Relación con Spring Security:
 *   Esta entidad NO es la que Spring Security usa internamente para autenticar.
 *   UserDetailsServiceImpl carga un User de aquí y lo convierte al objeto UserDetails
 *   que Spring Security sí entiende. Es el puente entre nuestra BD y el framework de seguridad.
 *
 * --- Anotaciones Lombok ---
 *
 * @Data             → genera getters, setters, equals, hashCode y toString automáticamente.
 * @NoArgsConstructor → constructor sin parámetros, requerido por JPA para instanciar entidades.
 * @AllArgsConstructor → constructor con todos los campos, útil en DataInitializer.
 */
@Entity
@Table(name = "users")   // nombre explícito de la tabla; "user" es palabra reservada en MySQL
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /**
     * Identificador único del usuario — clave primaria generada por MySQL.
     *
     * @Id → marca el campo como PRIMARY KEY.
     * @GeneratedValue(strategy = GenerationType.IDENTITY) → MySQL asigna el valor
     *   automáticamente con AUTO_INCREMENT. No necesitamos asignarlo en código.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Nombre de usuario para iniciar sesión — único en todo el sistema.
     *
     * @NotBlank → obligatorio, no puede ser vacío ni solo espacios.
     * @Size(min=3, max=50) → entre 3 y 50 caracteres. El mínimo evita nombres triviales.
     * @Column(nullable=false, unique=true, length=50) →
     *   - nullable=false: columna NOT NULL en MySQL
     *   - unique=true: Hibernate crea un índice UNIQUE, garantiza que no existan dos iguales
     *   - length=50: define VARCHAR(50) en la columna
     */
    @NotBlank(message = "El nombre de usuario es obligatorio")
    @Size(min = 3, max = 50, message = "El usuario debe tener entre 3 y 50 caracteres")
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    /**
     * Contraseña del usuario — siempre almacenada como hash BCrypt, NUNCA en texto plano.
     *
     * El hash BCrypt tiene el formato: $2a$10$... (60 caracteres aprox.)
     * Por eso la columna se crea sin longitud máxima explícita (TEXT en MySQL).
     *
     * @NotBlank → validación en el formulario de registro. Después del encode, el hash
     *             nunca será vacío, pero validamos el valor original ingresado por el usuario.
     * @Column(nullable = false) → la contraseña es obligatoria en la BD.
     */
    @NotBlank(message = "La contraseña es obligatoria")
    @Column(nullable = false)
    private String password;

    /**
     * Correo electrónico del usuario — único en el sistema.
     *
     * @NotBlank → obligatorio.
     * @Email → valida el formato del email con una expresión regular.
     *          Rechaza strings como "noesEmail" pero acepta "user@domain.com".
     * @Column(nullable=false, unique=true) → NOT NULL y UNIQUE en MySQL.
     */
    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    @Column(nullable = false, unique = true)
    private String email;

    /**
     * Rol del usuario en el sistema: USER o ADMIN.
     *
     * @Enumerated(EnumType.STRING) → guarda "USER" o "ADMIN" como texto en la BD.
     *   Se prefiere STRING sobre ORDINAL (índice numérico) porque si cambia el orden
     *   del enum en el futuro, los datos existentes no se corrompen.
     *
     * @Column(nullable = false) → el rol es obligatorio; toda cuenta debe tener uno.
     *
     * = UserRole.USER → valor por defecto en Java. Aunque UserService también lo establece,
     *   este default protege si alguien crea un User directamente sin pasar por el service.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    /**
     * Indica si la cuenta está activa o suspendida.
     * Si enabled = false, Spring Security rechaza el login aunque la contraseña sea correcta.
     * Útil para suspender usuarios sin borrarlos (conservando su historial de tareas).
     *
     * = true → toda cuenta nueva nace activa por defecto.
     */
    private boolean enabled = true;

    /**
     * Relación Uno-a-Muchos: un usuario puede tener muchas tareas.
     * Es el lado "inverso" (no dueño) de la relación; el dueño está en Task con @ManyToOne.
     *
     * mappedBy = "user" → le dice a JPA que la columna FK está en la tabla tasks (campo "user"),
     *                      no en users. Evita que Hibernate cree una tabla intermedia innecesaria.
     *
     * cascade = CascadeType.ALL → todas las operaciones se propagan a las tareas:
     *   Si se ELIMINA el usuario → se eliminan TODAS sus tareas automáticamente (CASCADE DELETE).
     *   Esto mantiene la integridad referencial sin necesidad de eliminarlas manualmente.
     *
     * fetch = FetchType.LAZY → las tareas NO se cargan de la BD al cargar el usuario.
     *   Solo se cargan cuando el código accede explícitamente a user.getTasks().
     *   Evita cargar listas enteras de tareas en cada operación que no las necesite.
     */
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Task> tasks;
}
