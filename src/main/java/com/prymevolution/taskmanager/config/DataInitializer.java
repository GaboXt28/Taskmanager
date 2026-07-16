package com.prymevolution.taskmanager.config;

import com.prymevolution.taskmanager.entity.*;
import com.prymevolution.taskmanager.repository.TaskRepository;
import com.prymevolution.taskmanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Inicializador de datos de demostración — se ejecuta automáticamente al arrancar la aplicación.
 *
 * ¿Para qué sirve?
 * Cuando la aplicación arranca por primera vez con una base de datos vacía, este componente
 * crea cuentas de usuario predefinidas y tareas de ejemplo, para que el sistema se vea
 * poblado desde el primer momento sin tener que registrar datos manualmente.
 *
 * Patrón aplicado: CommandLineRunner.
 * CommandLineRunner es una interfaz de Spring Boot con un único método: run(String... args).
 * Spring Boot detecta todos los beans que implementen esta interfaz y los ejecuta
 * automáticamente justo después de que el ApplicationContext esté completamente inicializado
 * (es decir, después de que Hibernate haya creado/actualizado el esquema de la BD).
 *
 * Característica de idempotencia:
 * El método verifica si ya existen usuarios antes de insertar.
 * Si la aplicación se reinicia y ya hay datos, no inserta duplicados.
 * Esto hace que el inicializador sea seguro de ejecutar múltiples veces.
 *
 * --- Cuentas creadas ---
 *   admin   / admin123  → rol ADMIN (acceso al panel de administración)
 *   usuario / user123   → rol USER  (acceso solo a sus propias tareas)
 *
 * @Component → registra esta clase como bean de Spring.
 *   Al implementar CommandLineRunner, Spring Boot la detecta y llama a run() automáticamente.
 *
 * @RequiredArgsConstructor → Lombok inyecta las tres dependencias vía constructor.
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    /** Para crear y guardar los usuarios de demo. */
    private final UserRepository userRepository;

    /** Para crear y guardar las tareas de demo. */
    private final TaskRepository taskRepository;

    /**
     * BCryptPasswordEncoder inyectado desde SecurityConfig.
     * Se usa para encriptar las contraseñas de los usuarios demo.
     * Las contraseñas nunca se guardan en texto plano, ni siquiera en datos de prueba.
     */
    private final PasswordEncoder passwordEncoder;

    /**
     * Método ejecutado automáticamente por Spring Boot al arrancar la aplicación.
     * Implementación del contrato de CommandLineRunner.
     *
     * Lógica de idempotencia: si ya existe al menos un usuario en la BD,
     * significa que los datos ya fueron sembrados en un arranque anterior y no hacemos nada.
     * Esto evita duplicar usuarios y tareas en cada reinicio de la aplicación.
     *
     * @param args argumentos de línea de comandos (no usamos ninguno aquí)
     */
    @Override
    public void run(String... args) {
        // Guardia de idempotencia: si ya hay usuarios, salimos sin hacer nada
        if (userRepository.count() > 0) return;

        // =========================================
        // Crear usuario ADMINISTRADOR
        // =========================================
        User admin = new User();
        admin.setUsername("admin");
        // passwordEncoder.encode() genera el hash BCrypt de la contraseña en texto plano
        // El hash resultante es algo como: $2a$10$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW
        admin.setPassword(passwordEncoder.encode("admin123"));
        admin.setEmail("admin@taskmanager.com");
        // Solo el admin creado aquí tiene rol ADMIN;
        // cualquier usuario que se registre por formulario recibirá UserRole.USER
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        userRepository.save(admin);

        // =========================================
        // Crear usuario regular de prueba
        // =========================================
        User user1 = new User();
        user1.setUsername("usuario");
        user1.setPassword(passwordEncoder.encode("user123"));
        user1.setEmail("usuario@taskmanager.com");
        user1.setRole(UserRole.USER);
        user1.setEnabled(true);
        userRepository.save(user1);

        // Crear tareas de demo para ambos usuarios
        // Así el dashboard se ve con datos reales desde el primer arranque
        crearTareas(admin);
        crearTareas(user1);
    }

    /**
     * Crea y guarda un conjunto de tareas de demostración para un usuario dado.
     *
     * Cada entrada del array representa una tarea con:
     *   [0] título
     *   [1] descripción
     *   [2] días desde hoy hasta el vencimiento (int)
     *   [3] prioridad (TaskPriority enum)
     *   [4] estado (TaskStatus enum)
     *
     * Se mezclan intencionalmente los tres estados y las tres prioridades para que
     * el panel de tareas muestre las tarjetas resumen con números variados.
     *
     * LocalDate.now().plusDays(n) → fecha de vencimiento relativa a hoy.
     * Así las fechas siempre son futuras independientemente de cuándo se arranque la app.
     *
     * @param user el usuario al que se asignarán todas las tareas creadas
     */
    private void crearTareas(User user) {
        // Cada fila: { título, descripción, díasHastaVencer, prioridad, estado }
        Object[][] data = {
            {"Configurar entorno de desarrollo",
             "Instalar JDK 17, Maven 3.9 y configurar el IDE IntelliJ IDEA",
             7, TaskPriority.ALTA, TaskStatus.COMPLETADA},

            {"Diseñar base de datos",
             "Crear diagrama Entidad-Relación y definir las tablas del sistema",
             10, TaskPriority.ALTA, TaskStatus.COMPLETADA},

            {"Implementar autenticación",
             "Configurar Spring Security con formulario de login, registro y roles",
             14, TaskPriority.ALTA, TaskStatus.EN_PROGRESO},

            {"Crear módulo de tareas CRUD",
             "Controladores, servicios, repositorios JPA y validadores",
             20, TaskPriority.MEDIA, TaskStatus.EN_PROGRESO},

            {"Aplicar estilos con Bootstrap 5",
             "Diseño responsivo para móviles, tablets y escritorio con Bootstrap",
             25, TaskPriority.MEDIA, TaskStatus.PENDIENTE},

            {"Escribir documentación técnica",
             "README.md con instrucciones de instalación, configuración y despliegue",
             30, TaskPriority.BAJA, TaskStatus.PENDIENTE},
        };

        // Iteramos el array y creamos cada Task con sus datos
        for (Object[] row : data) {
            Task t = new Task();

            // Asignamos cada campo accediendo a la posición correcta del array
            // y hacemos cast al tipo correcto (todos los elementos son Object por el array genérico)
            t.setTitle((String) row[0]);
            t.setDescription((String) row[1]);

            // LocalDate.now() obtiene la fecha actual del servidor (ej. 2026-06-11)
            // plusDays() suma los días indicados para calcular la fecha de vencimiento
            t.setDueDate(LocalDate.now().plusDays((int) row[2]));

            t.setPriority((TaskPriority) row[3]);
            t.setStatus((TaskStatus) row[4]);

            // Asignamos el usuario dueño — establece la FK user_id en la tabla tasks
            t.setUser(user);

            // @PrePersist en Task.java se encargará de establecer createdAt automáticamente
            taskRepository.save(t);
        }
    }
}
