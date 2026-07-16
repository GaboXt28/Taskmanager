package com.prymevolution.taskmanager.service;

import com.prymevolution.taskmanager.entity.Task;
import com.prymevolution.taskmanager.entity.TaskStatus;
import com.prymevolution.taskmanager.entity.User;
import com.prymevolution.taskmanager.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Servicio de lógica de negocio para la gestión de tareas.
 *
 * Patrón aplicado: Service (capa de lógica de negocio del modelo MVC).
 *
 * En la arquitectura en capas (Layered Architecture) del proyecto:
 *   Controller → Service → Repository → Base de Datos
 *
 * El Service es el intermediario entre el Controller (que recibe peticiones HTTP)
 * y el Repository (que accede a la BD). Su responsabilidad es aplicar las reglas
 * del negocio: ¿qué se puede hacer? ¿con qué restricciones?
 *
 * En este proyecto TaskService es relativamente simple porque las reglas de negocio
 * están mayormente en el controlador (verificar propiedad) y en el validador.
 * En un sistema más complejo, aquí vivirían reglas como notificaciones, auditoría, etc.
 *
 * --- Anotaciones de clase ---
 *
 * @Service → marca la clase como componente de lógica de negocio.
 *   Spring la registra como bean y la hace inyectable en controladores.
 *   Técnicamente es idéntico a @Component, pero semánticamente comunica su rol en la arquitectura.
 *
 * @RequiredArgsConstructor → anotación de Lombok que genera un constructor con todos los campos
 *   final como parámetros. Spring usa ese constructor para inyectar las dependencias (DI).
 *   Es la forma moderna recomendada de inyección de dependencias (sobre @Autowired en campo).
 *   Ventajas: las dependencias son inmutables (final) y la clase es más fácil de testear.
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /**
     * Dependencia al repositorio de tareas — inyectada por Spring vía constructor.
     * final garantiza que no puede reasignarse después de la construcción del objeto.
     */
    private final TaskRepository repo;

    /**
     * Devuelve todas las tareas de un usuario, ordenadas de más reciente a más antigua.
     * Delega directamente al repositorio porque no hay lógica adicional que aplicar.
     *
     * @param user el usuario dueño de las tareas
     * @return lista de tareas ordenadas por fecha de creación descendente
     */
    public List<Task> findByUser(User user) {
        return repo.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * Busca una tarea por su id.
     *
     * Devuelve Optional<Task> para comunicar explícitamente que la tarea podría no existir.
     * El llamador (controller) decide qué hacer: si no existe, muestra un mensaje de error.
     * Esto es más seguro que devolver null y arriesgarse a NullPointerException.
     *
     * @param id clave primaria de la tarea a buscar
     * @return Optional con la tarea si existe, vacío si no
     */
    public Optional<Task> findById(Long id) {
        return repo.findById(id);
    }

    /**
     * Guarda una tarea nueva o actualiza una existente.
     *
     * JpaRepository.save() tiene comportamiento dual:
     *   - Si task.getId() == null  → hace INSERT (tarea nueva)
     *   - Si task.getId() != null  → hace UPDATE (tarea existente)
     * Spring Data JPA determina esto verificando si el id es nulo o si el objeto ya existe en BD.
     *
     * @Transactional → envuelve la operación en una transacción de base de datos.
     *   Si algo falla dentro del método (excepción no chequeada), la transacción hace ROLLBACK
     *   automáticamente y la BD vuelve al estado anterior. Garantiza la integridad de los datos.
     *   Para lecturas (findBy...) no es necesario; solo para escrituras (save, delete).
     *
     * @param task la tarea a guardar o actualizar
     * @return la tarea con el id asignado (si era nueva) o los datos actualizados
     */
    @Transactional
    public Task save(Task task) {
        return repo.save(task);
    }

    /**
     * Elimina una tarea por su id.
     *
     * @Transactional → misma razón que en save(): protege la operación de borrado con rollback
     *   automático si ocurre algún error inesperado durante la eliminación.
     *
     * @param id clave primaria de la tarea a eliminar
     */
    @Transactional
    public void deleteById(Long id) {
        repo.deleteById(id);
    }

    /**
     * Cuenta las tareas de un usuario en un estado específico.
     * Se usa en el Controller para calcular los números de las tarjetas del panel.
     *
     * @param user   el usuario propietario de las tareas
     * @param status el estado a filtrar (PENDIENTE, EN_PROGRESO o COMPLETADA)
     * @return número de tareas del usuario con ese estado
     */
    public long countByUserAndStatus(User user, TaskStatus status) {
        return repo.countByUserAndStatus(user, status);
    }

    /**
     * Cuenta el total de tareas de TODOS los usuarios en un estado dado.
     * Exclusivo para el panel de administración — no debe llamarse para usuarios normales.
     *
     * @param status el estado a contar globalmente
     * @return cantidad total de tareas en ese estado
     */
    public long countAllByStatus(TaskStatus status) {
        return repo.countByStatus(status);
    }

    /**
     * Devuelve absolutamente todas las tareas del sistema, sin filtro por usuario.
     * Solo el administrador tiene acceso a esta información (controlado en AdminController).
     *
     * @return lista con todas las tareas de todos los usuarios
     */
    public List<Task> findAll() {
        return repo.findAll();
    }

    /**
     * Cuenta el total de tareas en el sistema (todos los usuarios).
     * Usado en el dashboard del administrador para mostrar la métrica global.
     *
     * @return número total de tareas registradas
     */
    public long countAll() {
        return repo.count();
    }
}
