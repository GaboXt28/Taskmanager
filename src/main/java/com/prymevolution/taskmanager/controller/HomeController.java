package com.prymevolution.taskmanager.controller;

import com.prymevolution.taskmanager.entity.TaskStatus;
import com.prymevolution.taskmanager.entity.User;
import com.prymevolution.taskmanager.service.TaskService;
import com.prymevolution.taskmanager.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controlador de la página de inicio ("/") y de la página de error 403.
 *
 * Patrón aplicado: Controller (capa de presentación del modelo MVC).
 *
 * Este controlador tiene un comportamiento dual en la misma URL ("/"):
 *   - Usuario NO autenticado → muestra la landing page con botones de registro/login
 *   - Usuario autenticado   → muestra un resumen de sus estadísticas de tareas
 *
 * Esta distinción se controla en el template index.html con:
 *   sec:authorize="!isAuthenticated()" → visible solo si NO está logueado
 *   sec:authorize="isAuthenticated()"  → visible solo si SÍ está logueado
 *
 * Los datos del Model solo se cargan si el usuario está autenticado (no tiene sentido
 * cargar estadísticas para un visitante anónimo).
 *
 * @Controller → controlador MVC de Spring que devuelve nombres de templates Thymeleaf.
 * @RequiredArgsConstructor → Lombok inyecta las dependencias vía constructor.
 */
@Controller
@RequiredArgsConstructor
public class HomeController {

    /** Para cargar las estadísticas de tareas del usuario logueado. */
    private final TaskService taskService;

    /** Para convertir el username de Spring Security a nuestra entidad User. */
    private final UserService userService;

    /**
     * Maneja la página de inicio de la aplicación.
     *
     * @GetMapping("/") → responde a GET /  (la raíz del servidor, ej: http://localhost:8081/)
     *
     * @param model       contenedor de datos para el template index.html
     * @param userDetails usuario actualmente logueado, o null si es anónimo.
     *   @AuthenticationPrincipal → inyecta el Principal del SecurityContext de Spring Security.
     *   Si nadie está logueado, Spring inyecta null (no lanza excepción).
     *   Esto nos permite manejar usuarios anónimos y autenticados en el mismo método.
     *
     * @return nombre del template: "index" → templates/index.html
     */
    @GetMapping("/")
    public String home(Model model, @AuthenticationPrincipal UserDetails userDetails) {

        // userDetails es null cuando la petición viene de un usuario no autenticado (anónimo)
        // En ese caso no cargamos estadísticas: el template muestra la landing page
        if (userDetails != null) {

            // Convertimos el UserDetails de Spring Security a nuestra entidad User de la BD
            // findByUsername puede devolver Optional vacío si el usuario fue eliminado
            // mientras tenía sesión activa (caso raro pero posible)
            User user = userService.findByUsername(userDetails.getUsername()).orElse(null);

            if (user != null) {
                // Calculamos las 4 estadísticas para las tarjetas de resumen del dashboard
                // findByUser().size() vs countByUserAndStatus(): usamos size() para el total porque
                // ya tenemos que cargar la lista de todas formas, y size() no hace consulta adicional
                model.addAttribute("total",       taskService.findByUser(user).size());
                model.addAttribute("pendientes",  taskService.countByUserAndStatus(user, TaskStatus.PENDIENTE));
                model.addAttribute("enProgreso",  taskService.countByUserAndStatus(user, TaskStatus.EN_PROGRESO));
                model.addAttribute("completadas", taskService.countByUserAndStatus(user, TaskStatus.COMPLETADA));
                // Nota: el template usa ${total ?: 0} como defensa adicional por si alguna variable
                //       no llegara al modelo; el operador ?: es "Elvis" de Thymeleaf (null-safe default)
            }
        }
        // Si userDetails es null, el Model queda vacío y Thymeleaf mostrará la landing page
        // El template se encarga de mostrar el contenido correcto según el estado de autenticación
        return "index";  // → templates/index.html
    }

    /**
     * Muestra la página de acceso denegado (Error 403 — Forbidden).
     *
     * Cuándo se llega aquí:
     *   Spring Security intercepta la petición de un usuario autenticado que intenta
     *   acceder a un recurso para el que no tiene permisos (ej. USER intenta /admin/**).
     *   SecurityConfig.accessDeniedPage("/error/403") redirige a esta URL.
     *   Este método simplemente renderiza el template correspondiente.
     *
     * @return nombre del template de error 403: "error/403" → templates/error/403.html
     */
    @GetMapping("/error/403")
    public String accessDenied() {
        return "error/403";  // → templates/error/403.html
    }
}
