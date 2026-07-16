package com.prymevolution.taskmanager.entity;

/**
 * Enumeración que define los roles de acceso del sistema.
 *
 * Control de Acceso Basado en Roles (RBAC — Role-Based Access Control):
 * En lugar de asignar permisos individuales a cada usuario, los permisos se asignan
 * a roles, y los usuarios reciben un rol. Esto simplifica la administración.
 *
 * Roles del sistema:
 *   USER  → usuario regular. Solo puede ver, crear, editar y eliminar SUS PROPIAS tareas.
 *   ADMIN → administrador. Tiene visibilidad global de todos los usuarios y todas las tareas,
 *           y puede eliminar cualquier tarea o usuario del sistema.
 *
 * Integración con Spring Security:
 *   En UserDetailsServiceImpl, el rol se convierte a autoridad con el prefijo "ROLE_":
 *   "ROLE_USER" o "ROLE_ADMIN".
 *   Esto es necesario porque Spring Security espera ese prefijo para que funcione hasRole().
 *
 *   En SecurityConfig:
 *     .requestMatchers("/admin/**").hasRole("ADMIN")  → equivale a tener la autoridad "ROLE_ADMIN"
 *
 *   En AdminController:
 *     @PreAuthorize("hasRole('ADMIN')")  → Spring verifica el rol antes de ejecutar el método.
 */
public enum UserRole {

    /** Usuario regular — acceso limitado a sus propias tareas. */
    USER,

    /** Administrador — acceso completo al sistema. */
    ADMIN
}
