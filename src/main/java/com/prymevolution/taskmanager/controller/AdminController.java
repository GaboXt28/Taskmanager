package com.prymevolution.taskmanager.controller;

import com.prymevolution.taskmanager.entity.TaskStatus;
import com.prymevolution.taskmanager.service.TaskService;
import com.prymevolution.taskmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Controlador del panel de administración — acceso exclusivo para usuarios con rol ADMIN.
 *
 * Patrón aplicado: Controller (capa de presentación del modelo MVC).
 *
 * Este controlador implementa el segundo nivel de autorización del sistema:
 *   - Primer nivel:  SecurityConfig protege la ruta /admin/** a nivel HTTP.
 *                    Si alguien accede sin el rol ADMIN, Spring Security lo redirige al 403.
 *   - Segundo nivel: @PreAuthorize en la clase verifica el rol antes de ejecutar cualquier método.
 *                    Es una doble protección (defense in depth).
 *
 * El panel de administración permite:
 *   - Ver TODOS los usuarios del sistema (no solo los propios)
 *   - Ver TODAS las tareas de todos los usuarios
 *   - Eliminar cualquier usuario (y sus tareas por cascada)
 *   - Eliminar cualquier tarea sin importar a quién pertenezca
 *
 * --- Anotaciones de clase ---
 *
 * @Controller → controlador MVC que devuelve nombres de plantillas Thymeleaf.
 *
 * @RequestMapping("/admin") → todas las rutas de este controlador empiezan con /admin.
 *
 * @PreAuthorize("hasRole('ADMIN')") → método de seguridad declarativo (Method Security).
 *   Spring evalúa esta expresión SpEL (Spring Expression Language) antes de ejecutar
 *   cualquier método del controlador. Si el usuario no tiene ROLE_ADMIN, lanza
 *   AccessDeniedException que SecurityConfig redirige a /error/403.
 *   Diferencia con SecurityConfig: SecurityConfig protege rutas HTTP; @PreAuthorize
 *   protege la lógica Java directamente. Juntos forman una doble barrera.
 *
 * @RequiredArgsConstructor → Lombok inyecta las dependencias vía constructor.
 */
@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    /** Service para gestionar usuarios: listar, contar, eliminar. */
    private final UserService userService;

    /** Service para gestionar tareas: listar, contar por estado, eliminar. */
    private final TaskService taskService;

    /**
     * Muestra el dashboard de administración con métricas globales del sistema.
     *
     * "Global" significa de TODOS los usuarios, no solo del admin logueado.
     * Esta visibilidad total es el privilegio diferenciador del rol ADMIN.
     *
     * @GetMapping (sin path) → responde a GET /admin
     *
     * @param model contenedor de datos para la plantilla Thymeleaf admin/dashboard.html
     * @return nombre de la plantilla del dashboard de administración
     */
    @GetMapping
    public String dashboard(Model model) {
        // Lista completa de todos los usuarios registrados en el sistema
        model.addAttribute("usuarios",      userService.findAll());

        // Lista completa de todas las tareas de todos los usuarios
        model.addAttribute("tasks",         taskService.findAll());

        // Métricas globales para las tarjetas de resumen del dashboard
        model.addAttribute("totalUsuarios", userService.countAll());
        model.addAttribute("totalTasks",    taskService.countAll());

        // Conteo de tareas por estado (de todos los usuarios, sin filtro por user)
        // countAllByStatus llama a countByStatus() en el repositorio (sin filtrar por usuario)
        model.addAttribute("pendientes",    taskService.countAllByStatus(TaskStatus.PENDIENTE));
        model.addAttribute("enProgreso",    taskService.countAllByStatus(TaskStatus.EN_PROGRESO));
        model.addAttribute("completadas",   taskService.countAllByStatus(TaskStatus.COMPLETADA));

        return "admin/dashboard";  // → templates/admin/dashboard.html
    }

    /**
     * Elimina un usuario y todas sus tareas (por cascada).
     *
     * @PostMapping("/usuarios/{id}/eliminar") → responde a POST /admin/usuarios/5/eliminar
     *   Se usa POST (no DELETE) porque HTML solo soporta GET y POST en formularios.
     *   El patrón {id} en la URL permite eliminar cualquier usuario por su id.
     *
     * El cascade ALL en User.java garantiza que al borrar el usuario,
     * Hibernate borra también TODAS sus tareas asociadas en la tabla tasks.
     * Esto mantiene la integridad referencial sin consultas manuales.
     *
     * Nota: el template HTML oculta el botón de borrar para el usuario "admin"
     * con th:if="${u.username != 'admin'}" para evitar que el admin se autoelimine.
     *
     * @param id    id del usuario a eliminar
     * @param flash para mostrar el mensaje de confirmación después del redirect
     * @return redirect al dashboard de administración
     */
    @PostMapping("/usuarios/{id}/eliminar")
    public String eliminarUsuario(@PathVariable Long id, RedirectAttributes flash) {
        userService.deleteById(id);
        // addFlashAttribute sobrevive el redirect y se muestra una sola vez en el template
        flash.addFlashAttribute("successMsg", "Usuario eliminado correctamente.");
        return "redirect:/admin";  // PRG Pattern: redirect para evitar reenvío en F5
    }

    /**
     * Elimina cualquier tarea del sistema, independientemente del usuario dueño.
     * Esta capacidad es exclusiva del administrador.
     *
     * Un usuario normal solo puede eliminar sus propias tareas (verificado en TaskController.findOwned()).
     * El administrador no tiene esa restricción: puede moderar el contenido del sistema.
     *
     * @param id    id de la tarea a eliminar
     * @param flash para mostrar el mensaje de confirmación después del redirect
     * @return redirect al dashboard de administración
     */
    @PostMapping("/tasks/{id}/eliminar")
    public String eliminarTask(@PathVariable Long id, RedirectAttributes flash) {
        taskService.deleteById(id);
        flash.addFlashAttribute("successMsg", "Tarea eliminada correctamente.");
        return "redirect:/admin";
    }
}
