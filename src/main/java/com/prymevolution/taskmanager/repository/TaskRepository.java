package com.prymevolution.taskmanager.repository;

import com.prymevolution.taskmanager.entity.Task;
import com.prymevolution.taskmanager.entity.TaskStatus;
import com.prymevolution.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad Task.
 *
 * Patrón aplicado: Repository (capa de acceso a datos del modelo MVC).
 * Esta interfaz es el único punto de contacto entre el código Java y la tabla "tasks" de MySQL.
 *
 * Principio de Responsabilidad Única (SRP): este repositorio solo se encarga de persistir y
 * recuperar tareas. La lógica de negocio vive en TaskService, no aquí.
 *
 * --- ¿Qué es JpaRepository? ---
 * JpaRepository<Task, Long> es una interfaz de Spring Data JPA que proporciona gratuitamente,
 * sin escribir ni una línea de SQL ni de código, los métodos CRUD más comunes:
 *
 *   save(task)           → INSERT o UPDATE según si el id es null o no
 *   findById(id)         → SELECT WHERE id = ? → devuelve Optional<Task>
 *   findAll()            → SELECT * FROM tasks
 *   deleteById(id)       → DELETE WHERE id = ?
 *   count()              → SELECT COUNT(*) FROM tasks
 *   existsById(id)       → SELECT EXISTS(SELECT 1 WHERE id = ?)
 *   ... y más
 *
 * Los parámetros genéricos: <Task, Long> = <Tipo de entidad, Tipo de la clave primaria>
 *
 * --- Query Methods (métodos derivados de nombre) ---
 * Spring Data JPA lee el nombre del método y genera el SQL automáticamente en tiempo de arranque.
 * Gramática: findBy[campo][Condición][Operador][campo2][Condición2]...
 *
 * --- @Repository ---
 * Marca esta interfaz como componente de acceso a datos.
 * Spring la registra como bean y también convierte las excepciones de JPA/SQL en
 * excepciones de Spring (DataAccessException), haciendo el manejo de errores más uniforme.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Devuelve todas las tareas de un usuario, ordenadas de la más reciente a la más antigua.
     *
     * Spring Data JPA descompone el nombre del método así:
     *   findBy       → SELECT * FROM tasks WHERE
     *   User         → user_id = :user
     *   OrderBy      → ORDER BY
     *   CreatedAt    → created_at
     *   Desc         → DESC
     *
     * SQL generado equivalente:
     *   SELECT * FROM tasks WHERE user_id = ? ORDER BY created_at DESC
     *
     * Se usa en TaskController y HomeController para mostrar el listado personal de cada usuario.
     *
     * @param user el usuario dueño de las tareas
     * @return lista de tareas del usuario, de más nueva a más antigua
     */
    List<Task> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Cuenta cuántas tareas de un usuario específico están en un estado determinado.
     *
     * SQL generado equivalente:
     *   SELECT COUNT(*) FROM tasks WHERE user_id = ? AND status = ?
     *
     * Se usa en TaskController para calcular los números de las tarjetas resumen
     * del panel de tareas (cuántas PENDIENTES, EN_PROGRESO, COMPLETADAS tiene el usuario).
     *
     * @param user   el usuario dueño de las tareas
     * @param status el estado a contar (PENDIENTE, EN_PROGRESO o COMPLETADA)
     * @return cantidad de tareas del usuario con ese estado
     */
    long countByUserAndStatus(User user, TaskStatus status);

    /**
     * Cuenta el total de tareas en un estado dado, de TODOS los usuarios.
     *
     * SQL generado equivalente:
     *   SELECT COUNT(*) FROM tasks WHERE status = ?
     *
     * Solo lo usa el AdminController para mostrar las métricas globales del sistema.
     * Un usuario normal nunca debería llamar a este método.
     *
     * @param status el estado a contar globalmente
     * @return cantidad total de tareas en ese estado (todos los usuarios)
     */
    long countByStatus(TaskStatus status);
}
