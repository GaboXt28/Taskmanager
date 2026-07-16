package com.prymevolution.taskmanager.entity;

/**
 * Enumeración que define los tres estados posibles del ciclo de vida de una tarea.
 *
 * ¿Por qué usar un enum y no un String libre?
 *   - Seguridad de tipos: el compilador impide asignar un estado inválido (ej. "CANCELADA").
 *   - Valores controlados: solo existen exactamente estos tres estados en todo el sistema.
 *   - Legibilidad: TaskStatus.COMPLETADA es más claro que el String "COMPLETADA".
 *   - Iteración fácil: TaskStatus.values() devuelve todos los estados para llenar selects en HTML.
 *
 * En la base de datos se guarda como VARCHAR con el nombre exacto del enum
 * (PENDIENTE, EN_PROGRESO, COMPLETADA) gracias a @Enumerated(EnumType.STRING) en Task.java.
 *
 * El campo displayName sirve para mostrar texto legible en la interfaz HTML con Thymeleaf
 * usando ${t.status.displayName} en lugar del nombre técnico del enum.
 */
public enum TaskStatus {

    /** La tarea acaba de crearse, aún no se ha comenzado a trabajar en ella. */
    PENDIENTE("Pendiente"),

    /** Se está trabajando activamente en la tarea. */
    EN_PROGRESO("En Progreso"),

    /** La tarea fue finalizada. */
    COMPLETADA("Completada");

    // Texto legible para mostrar en la interfaz de usuario (etiquetas, badges, selects)
    private final String displayName;

    /**
     * Constructor del enum — cada constante llama a este constructor con su displayName.
     * En Java, los constructores de enum son siempre privados (implícitamente).
     *
     * @param displayName texto amigable para mostrar en pantalla
     */
    TaskStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Getter para acceder al displayName desde Thymeleaf o desde código Java.
     * Ejemplo de uso en HTML: th:text="${estado.displayName}"
     *
     * @return texto legible del estado (ej. "En Progreso")
     */
    public String getDisplayName() {
        return displayName;
    }
}
