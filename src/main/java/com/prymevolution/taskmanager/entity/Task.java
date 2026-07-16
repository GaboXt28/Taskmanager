package com.prymevolution.taskmanager.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Entidad JPA que representa una tarea dentro del sistema.
 *
 * Patrón aplicado: Entity (capa de dominio del modelo MVC).
 * Cada instancia de esta clase equivale a una fila en la tabla "tasks" de MySQL.
 *
 * --- Anotaciones de clase ---
 *
 * @Entity  → le indica a JPA/Hibernate que esta clase es una entidad persistente.
 *            Hibernate usará esta clase para generar/mapear la tabla correspondiente en MySQL.
 *
 * @Table(name = "tasks") → define explícitamente el nombre de la tabla en la base de datos.
 *                          Sin esta anotación, Hibernate usaría el nombre de la clase ("task").
 *
 * @Data             → anotación de Lombok que genera automáticamente en tiempo de compilación:
 *                     getters, setters, equals(), hashCode() y toString() para todos los campos.
 *                     Elimina decenas de líneas de código repetitivo (boilerplate).
 *
 * @NoArgsConstructor → Lombok genera un constructor vacío: new Task().
 *                      JPA lo exige obligatoriamente para poder instanciar la entidad al leer filas de la BD.
 *
 * @AllArgsConstructor → Lombok genera un constructor con todos los campos como parámetros.
 *                       Útil para crear objetos completamente inicializados en tests o en DataInitializer.
 */
@Entity
@Table(name = "tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    /**
     * Clave primaria de la tabla tasks.
     *
     * @Id → le dice a JPA que este campo es la PRIMARY KEY de la tabla.
     *
     * @GeneratedValue(strategy = GenerationType.IDENTITY) → indica que MySQL es quien genera
     *   el valor del id usando AUTO_INCREMENT. No necesitamos asignarlo manualmente nunca.
     *   Otras estrategias posibles: SEQUENCE (PostgreSQL), TABLE (portable), AUTO (depende del dialecto).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Título de la tarea — campo obligatorio y con límite de longitud.
     *
     * @NotBlank → validación de Jakarta Bean Validation: el campo no puede ser null, ni vacío (""),
     *             ni contener solo espacios en blanco ("   "). Es más estricto que @NotNull y @NotEmpty.
     *
     * @Size(max = 120) → valida que la cadena no supere 120 caracteres.
     *                    El mensaje se muestra en el formulario HTML con th:errors.
     *
     * @Column(nullable = false) → mapea esta restricción directamente al DDL de MySQL:
     *                             la columna se crea con NOT NULL en la base de datos.
     */
    @NotBlank(message = "El título no puede estar vacío")
    @Size(max = 120, message = "El título no puede superar 120 caracteres")
    @Column(nullable = false)
    private String title;

    /**
     * Descripción detallada de la tarea — campo opcional.
     *
     * @Size(max = 500) → límite de 500 caracteres, coincide con el maxlength del textarea HTML.
     *
     * @Column(length = 500) → Hibernate genera la columna como VARCHAR(500) en MySQL.
     *                         Sin este parámetro, el default sería VARCHAR(255).
     *
     * No tiene @NotBlank porque la descripción es opcional (puede ser null en la BD).
     */
    @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
    @Column(length = 500)
    private String description;

    /**
     * Fecha límite para completar la tarea.
     *
     * @NotNull → la fecha es obligatoria. A diferencia de @NotBlank (para Strings),
     *            @NotNull sirve para cualquier tipo de objeto: Date, enum, Integer, etc.
     *
     * @FutureOrPresent → valida que la fecha sea hoy o en el futuro. Si el usuario ingresa
     *                    una fecha pasada, la validación falla con el mensaje indicado.
     *
     * @DateTimeFormat(pattern = "yyyy-MM-dd") → le dice a Spring MVC cómo convertir el String
     *   "2026-12-31" que llega del input type="date" del HTML a un objeto LocalDate de Java.
     *   Sin esta anotación, el binding fallaría con error de tipo.
     */
    @NotNull(message = "La fecha de vencimiento es obligatoria")
    @FutureOrPresent(message = "La fecha debe ser actual o futura")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    /**
     * Nivel de prioridad de la tarea: ALTA, MEDIA o BAJA.
     *
     * @NotNull → la prioridad es obligatoria; el usuario debe seleccionar una opción.
     *
     * @Enumerated(EnumType.STRING) → le dice a Hibernate que guarde el nombre del enum
     *   como texto en la BD ("ALTA", "MEDIA", "BAJA"), en lugar del índice numérico (0, 1, 2).
     *   Usar STRING es mucho más legible y seguro: si reordenamos el enum, los datos existentes
     *   siguen siendo válidos. Con EnumType.ORDINAL eso no está garantizado.
     */
    @NotNull(message = "Debe seleccionar una prioridad")
    @Enumerated(EnumType.STRING)
    private TaskPriority priority;

    /**
     * Estado actual de la tarea en su ciclo de vida: PENDIENTE, EN_PROGRESO o COMPLETADA.
     *
     * @Enumerated(EnumType.STRING) → misma razón que priority: guardamos el texto, no el índice.
     *
     * @Column(nullable = false) → el estado nunca puede ser null en la BD.
     *
     * = TaskStatus.PENDIENTE → valor por defecto en Java: toda tarea nueva arranca como PENDIENTE.
     *   Esto se refuerza también en @PrePersist por si el campo llega null desde el form.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDIENTE;

    /**
     * Relación Muchos-a-Uno con la entidad User.
     * Muchas tareas pertenecen a un solo usuario.
     *
     * @ManyToOne → define la cardinalidad: N tareas → 1 usuario.
     *              Hibernate crea una columna de clave foránea (user_id) en la tabla tasks.
     *
     * fetch = FetchType.LAZY → carga diferida: Hibernate NO trae los datos del usuario de la BD
     *   al cargar la tarea. Solo los carga si el código llama explícitamente a task.getUser().
     *   Esto evita consultas innecesarias (problema N+1) y mejora el rendimiento.
     *   La alternativa EAGER cargaría el usuario siempre, aunque no se necesite.
     *
     * @JoinColumn(name = "user_id") → especifica el nombre de la columna FK en la tabla tasks.
     *   nullable = false → toda tarea debe tener un dueño, no se permiten tareas huérfanas.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Fecha y hora exacta en que se creó la tarea — se establece automáticamente.
     *
     * @Column(updatable = false) → Hibernate excluye este campo de los UPDATE SQL.
     *   Una vez insertado el registro, la fecha de creación nunca cambia en la BD.
     *   Esto garantiza la integridad del dato de auditoría.
     *
     * Se usa LocalDateTime (con hora) en lugar de LocalDate (solo fecha) para mayor precisión.
     */
    @Column(updatable = false)
    private LocalDateTime createdAt;

    /**
     * Método de ciclo de vida JPA — se ejecuta justo ANTES de que Hibernate haga el INSERT.
     *
     * @PrePersist → Spring/JPA intercepta la operación de guardado y ejecuta este método
     *   automáticamente. Equivale a un "trigger" de aplicación (no de base de datos).
     *
     * Usos aquí:
     *   1. Establecer createdAt con la fecha/hora actual del servidor.
     *      No lo ponemos en el campo directamente (= LocalDateTime.now()) porque eso se evaluaría
     *      en el momento en que se carga la clase, no cuando se crea el objeto.
     *   2. Garantizar que status nunca sea null si llega sin valor desde el formulario.
     */
    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) status = TaskStatus.PENDIENTE;
    }
}
