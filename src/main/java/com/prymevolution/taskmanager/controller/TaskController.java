package com.prymevolution.taskmanager.controller;

import com.prymevolution.taskmanager.entity.*;
import com.prymevolution.taskmanager.service.TaskService;
import com.prymevolution.taskmanager.service.UserService;
import com.prymevolution.taskmanager.validator.TaskValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Controlador principal — gestiona las operaciones CRUD de tareas.
 *
 * Patrón aplicado: Controller (capa de presentación del modelo MVC).
 * En el patrón MVC:
 *   Model      → Task, TaskStatus, TaskPriority (entidades)
 *   View       → templates/tasks/list.html y templates/tasks/form.html (Thymeleaf)
 *   Controller → esta clase (recibe peticiones, llama al service, prepara datos para la vista)
 *
 * Este controlador sigue el flujo POST/Redirect/GET (PRG Pattern):
 *   POST (procesar formulario) → Redirect (evitar reenvío al recargar) → GET (mostrar resultado)
 * Esto evita que el usuario reenvíe el formulario accidentalmente al pulsar F5.
 *
 * --- Anotaciones de clase ---
 *
 * @Controller → marca la clase como controlador MVC de Spring. Los métodos anotados con
 *   @GetMapping/@PostMapping manejan peticiones HTTP y devuelven nombres de plantillas Thymeleaf.
 *   A diferencia de @RestController, los métodos devuelven HTML renderizado, no JSON.
 *
 * @RequestMapping("/tasks") → prefijo de ruta que se aplica a TODOS los métodos de esta clase.
 *   Equivale a que cada método hereda "/tasks" antes de su propia ruta.
 *   Resultado: /tasks, /tasks/nueva, /tasks/editar/{id}, /tasks/eliminar/{id}
 *
 * @RequiredArgsConstructor → Lombok genera el constructor con las tres dependencias (final fields).
 *   Spring inyecta TaskService, UserService y TaskValidator automáticamente al crear el controlador.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    /** Lógica de negocio para tareas: CRUD, conteos. */
    private final TaskService taskService;

    /** Para buscar el objeto User completo de la BD a partir del nombre de usuario del login. */
    private final UserService userService;

    /** Validador de reglas de negocio para la entidad Task. */
    private final TaskValidator validator;

    // =========================================================================
    // LISTAR TAREAS (GET /tasks)
    // =========================================================================

    /**
     * Muestra el listado de tareas del usuario logueado junto con sus estadísticas.
     *
     * @GetMapping (sin path) → responde a GET /tasks (hereda el prefijo de la clase)
     *
     * @param model       contenedor de datos que Thymeleaf puede leer en el HTML (${variable})
     * @param userDetails objeto de Spring Security con el nombre del usuario logueado.
     *   @AuthenticationPrincipal → inyecta automáticamente el Principal (usuario autenticado)
     *   desde el SecurityContext sin necesidad de llamar a SecurityContextHolder.getContext().
     *
     * @return nombre de la plantilla Thymeleaf: "tasks/list" → resources/templates/tasks/list.html
     */
    @GetMapping
    public String listar(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        // Convertimos el UserDetails (de Spring Security) a nuestro User (de la BD)
        User user = getUser(userDetails);

        // Consultamos las tareas del usuario ordenadas de más nueva a más antigua
        List<Task> lista = taskService.findByUser(user);

        // Agregamos todos los datos al Model para que Thymeleaf los use en el template
        // Cada model.addAttribute("nombre", valor) está disponible en HTML como ${nombre}
        model.addAttribute("tasks",       lista);            // lista completa para la tabla
        model.addAttribute("total",       lista.size());     // contador total para la tarjeta
        model.addAttribute("pendientes",  taskService.countByUserAndStatus(user, TaskStatus.PENDIENTE));
        model.addAttribute("enProgreso",  taskService.countByUserAndStatus(user, TaskStatus.EN_PROGRESO));
        model.addAttribute("completadas", taskService.countByUserAndStatus(user, TaskStatus.COMPLETADA));

        return "tasks/list";  // Thymeleaf busca: src/main/resources/templates/tasks/list.html
    }

    // =========================================================================
    // NUEVA TAREA — MOSTRAR FORMULARIO (GET /tasks/nueva)
    // =========================================================================

    /**
     * Muestra el formulario vacío para crear una nueva tarea.
     *
     * Se agrega un objeto Task vacío (new Task()) al modelo para que Thymeleaf lo enlace
     * con el formulario usando th:object="${task}". Sin este objeto, th:field="*{title}"
     * fallaría al intentar acceder a un objeto inexistente.
     *
     * @return plantilla del formulario en modo "nueva tarea"
     */
    @GetMapping("/nueva")
    public String nuevaForm(Model model) {
        model.addAttribute("task",        new Task());              // objeto vacío para el binding del form
        model.addAttribute("prioridades", TaskPriority.values()); // opciones del select de prioridad
        model.addAttribute("estados",     TaskStatus.values());   // opciones del select de estado
        model.addAttribute("formTitle",   "Nueva Tarea");         // título que muestra el HTML
        model.addAttribute("isEdit",      false);                 // el HTML usa esto para mostrar/ocultar campos
        return "tasks/form";
    }

    // =========================================================================
    // NUEVA TAREA — PROCESAR FORMULARIO (POST /tasks/nueva)
    // =========================================================================

    /**
     * Procesa el formulario de creación de tarea. Valida y guarda si todo está correcto.
     *
     * @Valid → le dice a Spring que aplique las anotaciones de validación de la entidad
     *   (@NotBlank, @Size, @NotNull, @FutureOrPresent) antes de entrar al método.
     *   Los errores se acumulan en BindingResult.
     *
     * @ModelAttribute("task") → enlaza los campos del formulario HTML con el objeto Task.
     *   Spring convierte cada parámetro del POST (title, description, dueDate, etc.)
     *   al tipo correcto y los asigna a los campos del objeto automáticamente.
     *
     * BindingResult → contiene los errores de validación. DEBE ir inmediatamente después
     *   del objeto que se valida. Si no, Spring lanza excepción en lugar de acumular errores.
     *
     * @AuthenticationPrincipal UserDetails → usuario logueado para asignarle la tarea como dueño.
     *
     * RedirectAttributes → permite enviar datos a través de un redirect (PRG Pattern).
     *   addFlashAttribute() guarda el dato en la sesión HTTP temporalmente.
     *   Sobrevive al redirect y desaparece después de ser leído una vez.
     *   Thymeleaf lo muestra en el template con ${successMsg}.
     *
     * @return si hay errores → vuelve al formulario; si todo OK → redirect a la lista
     */
    @PostMapping("/nueva")
    public String guardarNueva(@Valid @ModelAttribute("task") Task task,
                               BindingResult result,
                               @AuthenticationPrincipal UserDetails userDetails,
                               RedirectAttributes flash, Model model) {

        // Ejecutamos el validador personalizado además de las anotaciones de @Valid
        // Los errores del validator se acumulan en el mismo BindingResult
        validator.validate(task, result);

        // Si hay algún error de validación, volvemos al formulario con los errores visibles
        if (result.hasErrors()) {
            // Hay que volver a agregar las listas de opciones porque el Model se pierde
            // al regresar al formulario (no es un redirect, es un forward interno)
            model.addAttribute("prioridades", TaskPriority.values());
            model.addAttribute("estados",     TaskStatus.values());
            model.addAttribute("formTitle",   "Nueva Tarea");
            model.addAttribute("isEdit",      false);
            return "tasks/form";  // devolvemos la vista con el objeto task que tiene los errores
        }

        // Todo válido: asignamos el usuario logueado como dueño de la tarea
        // task.getUser() sería null aquí si no lo asignamos explícitamente
        task.setUser(getUser(userDetails));

        // Guardamos la tarea en MySQL — el @PrePersist de Task.java establecerá createdAt
        taskService.save(task);

        // Mensaje flash: sobrevive el redirect y se muestra una vez en la lista
        flash.addFlashAttribute("successMsg", "Tarea registrada exitosamente.");

        // Redirect al listado (patrón PRG: Post → Redirect → Get)
        return "redirect:/tasks";
    }

    // =========================================================================
    // EDITAR TAREA — MOSTRAR FORMULARIO (GET /tasks/editar/{id})
    // =========================================================================

    /**
     * Muestra el formulario precargado con los datos actuales de la tarea a editar.
     *
     * @PathVariable Long id → extrae el {id} de la URL y lo convierte a Long.
     *   Ejemplo: GET /tasks/editar/5 → id = 5
     *
     * Seguridad: verificamos que la tarea pertenezca al usuario logueado.
     * Un usuario no puede editar las tareas de otro aunque conozca su id.
     *
     * @return formulario con los datos de la tarea, o redirect si no tiene permiso
     */
    @GetMapping("/editar/{id}")
    public String editarForm(@PathVariable Long id, Model model,
                             @AuthenticationPrincipal UserDetails userDetails,
                             RedirectAttributes flash) {

        // findOwned verifica que la tarea exista Y que pertenezca al usuario logueado
        // Si no pasa la verificación, devuelve null y el método redirige al listado
        Task t = findOwned(id, userDetails, flash);
        if (t == null) return "redirect:/tasks";

        model.addAttribute("task",        t);                     // tarea con datos para precargar el form
        model.addAttribute("prioridades", TaskPriority.values()); // opciones del select
        model.addAttribute("estados",     TaskStatus.values());   // en edición sí se muestra el selector de estado
        model.addAttribute("formTitle",   "Editar Tarea");
        model.addAttribute("isEdit",      true);  // el HTML lo usa para mostrar el campo de estado (solo en edición)
        return "tasks/form";
    }

    // =========================================================================
    // EDITAR TAREA — PROCESAR FORMULARIO (POST /tasks/editar/{id})
    // =========================================================================

    /**
     * Procesa el formulario de edición. Valida, fusiona con datos originales y guarda.
     *
     * El formulario HTML solo envía los campos visibles (title, description, dueDate, priority, status).
     * Campos como createdAt y user NO vienen en el POST y quedarían null si no los restauramos.
     * Por eso buscamos la tarea original y copiamos esos campos al objeto recibido del form.
     */
    @PostMapping("/editar/{id}")
    public String actualizarTask(@PathVariable Long id,
                                 @Valid @ModelAttribute("task") Task task,
                                 BindingResult result,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes flash, Model model) {

        // Aplicar validaciones de anotaciones + validaciones personalizadas
        validator.validate(task, result);
        if (result.hasErrors()) {
            model.addAttribute("prioridades", TaskPriority.values());
            model.addAttribute("estados",     TaskStatus.values());
            model.addAttribute("formTitle",   "Editar Tarea");
            model.addAttribute("isEdit",      true);
            return "tasks/form";
        }

        // Recuperamos la tarea original para obtener los campos que el form no envió
        // También re-verificamos la propiedad por seguridad (podría ser un ataque CSRF)
        Task existing = findOwned(id, userDetails, flash);
        if (existing == null) return "redirect:/tasks";

        // Restauramos los campos que no deben cambiar nunca:
        task.setId(id);                          // el id garantiza que JPA haga UPDATE, no INSERT
        task.setUser(existing.getUser());         // el dueño de la tarea no cambia
        task.setCreatedAt(existing.getCreatedAt()); // la fecha de creación es inmutable

        // @Column(updatable=false) en createdAt hace que Hibernate omita ese campo en el SQL UPDATE
        // igualmente restauramos el valor para que el objeto Java quede consistente
        taskService.save(task);  // JPA detecta id != null → genera UPDATE en lugar de INSERT

        flash.addFlashAttribute("successMsg", "Tarea actualizada exitosamente.");
        return "redirect:/tasks";
    }

    // =========================================================================
    // ELIMINAR TAREA (POST /tasks/eliminar/{id})
    // =========================================================================

    /**
     * Elimina una tarea después de verificar que pertenece al usuario logueado.
     *
     * Se usa POST (no DELETE) porque los formularios HTML solo soportan GET y POST.
     * El modal de confirmación en list.html genera un formulario con method="post".
     *
     * @return redirect al listado con mensaje de confirmación o error
     */
    @PostMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails userDetails,
                           RedirectAttributes flash) {

        // Verificamos propiedad antes de eliminar — ningún usuario puede borrar tareas ajenas
        Task t = findOwned(id, userDetails, flash);
        if (t != null) {
            taskService.deleteById(id);
            flash.addFlashAttribute("successMsg", "Tarea eliminada correctamente.");
        }
        // Si t == null, findOwned ya agregó el mensaje de error al flash
        return "redirect:/tasks";
    }

    // =========================================================================
    // MÉTODOS AUXILIARES PRIVADOS
    // =========================================================================

    /**
     * Convierte el UserDetails de Spring Security en nuestra entidad User de la BD.
     *
     * Spring Security trabaja con UserDetails (interfaz propia del framework).
     * Nuestro código necesita la entidad User (con id, role, etc.).
     * Este método hace ese puente consultando la BD por el username.
     *
     * @param ud UserDetails del usuario logueado (proviene de @AuthenticationPrincipal)
     * @return la entidad User correspondiente de la base de datos
     * @throws RuntimeException si el usuario no existe en BD (no debería ocurrir en condiciones normales)
     */
    private User getUser(UserDetails ud) {
        return userService.findByUsername(ud.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + ud.getUsername()));
    }

    /**
     * Busca una tarea y verifica que pertenezca al usuario logueado.
     * Implementa el control de acceso a nivel de recurso (object-level authorization).
     *
     * Spring Security protege rutas por roles (ADMIN/USER), pero no sabe qué tareas
     * pertenecen a qué usuario. Este método aplica esa verificación adicional.
     *
     * Si la tarea no existe o no pertenece al usuario → agrega un mensaje de error
     * al flash y devuelve null. El método que llama detecta el null y hace redirect.
     *
     * @param id  id de la tarea a buscar
     * @param ud  usuario logueado
     * @param flash para agregar el mensaje de error si la verificación falla
     * @return la tarea si existe y pertenece al usuario; null en caso contrario
     */
    private Task findOwned(Long id, UserDetails ud, RedirectAttributes flash) {
        Task t = taskService.findById(id).orElse(null);

        // Verificación doble:
        // 1. t == null → la tarea no existe en la BD (id inválido o ya fue eliminada)
        // 2. !t.getUser().getUsername().equals(ud.getUsername()) → la tarea existe pero es de otro usuario
        if (t == null || !t.getUser().getUsername().equals(ud.getUsername())) {
            flash.addFlashAttribute("errorMsg", "Tarea no encontrada.");
            return null;
        }
        return t;
    }
}
