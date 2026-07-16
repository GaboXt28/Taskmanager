package com.prymevolution.taskmanager.entity;

/**
 * Enumeración que define los tres niveles de prioridad de una tarea.
 *
 * ¿Por qué enum y no una tabla de base de datos?
 *   - Los niveles de prioridad son valores fijos y conocidos en tiempo de diseño.
 *   - No se necesita agregar, modificar ni eliminar prioridades en tiempo de ejecución.
 *   - Un enum es más eficiente: no requiere un JOIN adicional al consultar tareas.
 *
 * La prioridad se usa en la UI para mostrar badges de colores con Bootstrap:
 *   ALTA  → badge rojo    (badge-alta  en styles.css)
 *   MEDIA → badge naranja (badge-media en styles.css)
 *   BAJA  → badge gris    (badge-baja  en styles.css)
 *
 * El CSS aplica el estilo con: th:class="'badge badge-' + ${t.priority.name().toLowerCase()}"
 * lo que produce: "badge badge-alta", "badge badge-media" o "badge badge-baja".
 */
public enum TaskPriority {

    /** Urgente — requiere atención inmediata. */
    ALTA("Alta"),

    /** Importante pero no crítica — puede planificarse. */
    MEDIA("Media"),

    /** Puede hacerse cuando haya tiempo disponible. */
    BAJA("Baja");

    // Texto amigable que se muestra en la interfaz HTML
    private final String displayName;

    /**
     * Constructor del enum para asociar cada constante con su etiqueta legible.
     *
     * @param displayName texto para mostrar en la UI (ej. "Alta")
     */
    TaskPriority(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Devuelve el texto legible de la prioridad.
     * Usado en Thymeleaf: th:text="${t.priority.displayName}"
     *
     * @return nombre legible de la prioridad
     */
    public String getDisplayName() {
        return displayName;
    }
}
