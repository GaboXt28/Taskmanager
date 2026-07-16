# TaskManager - PrymEvolution

Sistema de Gestión de Tareas desarrollado con **Spring Boot**, **Thymeleaf**, **Spring Security 6 + JWT** y **MySQL**.

Proyecto universitario que implementa un CRUD completo de tareas con autenticación **stateless** basada en **JSON Web Tokens (JWT)**, control de acceso por roles (`USER` / `ADMIN`) y una interfaz web renderizada del lado del servidor con Thymeleaf y Bootstrap 5.

## Tecnologías

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.3.5 |
| Frontend | Thymeleaf + Bootstrap 5.3.3 + Font Awesome |
| Base de Datos | MySQL 8+ (o MariaDB 10.4+) con Spring Data JPA / Hibernate |
| Seguridad | Spring Security 6 + JWT (JJWT 0.12) + BCrypt + roles |
| Build | Maven 3.9+ |
| Java | Java 17+ |

## Funcionalidades

- **Autenticación stateless con JWT**: el servidor no almacena sesiones; cada petición se autentica a partir del token.
- **Roles**: `USER` (acceso a sus propias tareas) y `ADMIN` (panel global de administración).
- **CRUD de Tareas**: crear, listar, editar y eliminar, con verificación de propiedad del recurso.
- **API REST de autenticación**: endpoint JSON para obtener el token (ideal para Postman o un frontend externo).
- **Validación**: título obligatorio, fecha futura/presente, prioridad requerida.
- **Dashboard**: métricas de tareas por estado (personal y global para el admin).
- **Diseño Responsivo**: Bootstrap 5, adaptado a móvil, tablet y escritorio.

## Arquitectura de seguridad: JWT stateless

La aplicación **no usa `HttpSession`**. Toda la autenticación viaja en un JSON Web Token firmado con el algoritmo `HS256`, compuesto por:

- **Header**: algoritmo de firma (`HS256`) y tipo (`JWT`).
- **Payload**: `subject` (username), `roles` (autoridades del usuario), `iat` (fecha de emisión) y `exp` (fecha de expiración).
- **Signature**: HMAC-SHA256 del header + payload, firmado con la clave secreta de `jwt.secret`. Garantiza que el token no pueda alterarse sin invalidar la firma.

### Componentes

| Componente | Responsabilidad |
|---|---|
| `security/JwtUtil.java` | Genera y valida el token (firma, expiración, extracción de claims). |
| `security/JwtAuthenticationFilter.java` | Filtro (`OncePerRequestFilter`) que intercepta cada petición, extrae el token de la cabecera `Authorization: Bearer <token>` o de la cookie `jwt`, lo valida y reconstruye el `SecurityContextHolder`. |
| `security/RestAwareAuthenticationEntryPoint.java` | Responde 401 JSON en rutas `/api/**` o redirige a `/login` en páginas web cuando falta autenticación. |
| `security/RestAwareAccessDeniedHandler.java` | Responde 403 JSON en rutas `/api/**` o redirige a `/error/403` cuando el rol no alcanza. |
| `service/AuthService.java` | Punto único de autenticación: valida credenciales con `AuthenticationManager` y emite el JWT con `JwtUtil`. |
| `controller/api/AuthRestController.java` | `POST /api/auth/login` — API REST pura: recibe JSON, devuelve el JWT en el cuerpo de la respuesta. |
| `controller/AuthController.java` | `POST /login` / `POST /logout` — pensados para el navegador: guardan el JWT en una cookie `HttpOnly` para que las páginas Thymeleaf sigan funcionando sin JavaScript adicional. |
| `config/SecurityConfig.java` | `SessionCreationPolicy.STATELESS`, CSRF deshabilitado, rutas públicas/protegidas, registro del filtro JWT y `@EnableMethodSecurity` para que `@PreAuthorize` funcione. |

### Dos formas de autenticarse

**1. Como API REST (Postman, curl, un frontend externo):**

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

Respuesta:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "roles": ["ROLE_ADMIN"]
}
```

Ese `token` se envía en las siguientes peticiones como:

```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**2. Desde el navegador (interfaz Thymeleaf):**

El formulario de `/login` sigue funcionando igual que antes para el usuario final: al enviarlo, el servidor valida las credenciales, genera el JWT y lo guarda en una cookie `HttpOnly` llamada `jwt`. El navegador la reenvía automáticamente en cada petición y `JwtAuthenticationFilter` la usa para autenticar — sin que el usuario note ninguna diferencia respecto al login basado en sesión que había antes.

### Roles y autorización

- `SecurityConfig` protege las rutas a nivel HTTP: `/admin/**` exige `ROLE_ADMIN`, `/tasks/**` exige cualquier usuario autenticado.
- `AdminController` añade una segunda capa con `@PreAuthorize("hasRole('ADMIN')")` a nivel de método (defensa en profundidad), habilitada por `@EnableMethodSecurity` en `SecurityConfig`.
- `TaskController` verifica además la propiedad del recurso (un `USER` no puede editar/eliminar tareas de otro usuario aunque conozca su `id`).

## Requisitos Previos

- **JDK 17** o superior
- **Maven 3.9+** (o usa el wrapper `mvnw` / `mvnw.cmd` si está presente)
- **MySQL 8+** o **MariaDB 10.4+**
- Un cliente HTTP para probar la API (Postman, Insomnia o `curl`) — opcional pero recomendado

## Instalación y despliegue paso a paso

### 1. Clonar el repositorio

```bash
git clone <URL-DEL-REPOSITORIO>
cd task-manager
```

### 2. Crear la base de datos y las tablas

Ejecuta el script de esquema incluido en `database/schema.sql`:

```bash
mysql -u root -p < database/schema.sql
```

Esto crea la base de datos `taskmanager_db` (si no existe) y las tablas `users` y `tasks` con sus claves foráneas y restricciones `UNIQUE`.

### 3. Insertar los datos iniciales

```bash
mysql -u root -p < database/data.sql
```

Esto crea las dos cuentas de prueba con contraseñas ya encriptadas en BCrypt:

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `usuario` | `user123` | USER |

> Nota: si prefieres no ejecutar los scripts manualmente, la aplicación también sembrará estas mismas cuentas automáticamente en el primer arranque (ver `config/DataInitializer.java`), siempre que la tabla `users` esté vacía. Los scripts SQL son la vía recomendada para una instalación reproducible y para cumplir con el requisito de scripts separados de creación de datos.

### 4. Configurar las credenciales (variables de entorno)

**Ninguna credencial va escrita en el código ni en `application.properties`.** Se leen desde variables de entorno; si no las defines, se usan valores por defecto pensados solo para desarrollo local (`root` sin contraseña, típico de XAMPP/MySQL local).

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `DB_URL` | URL JDBC de conexión a MySQL | `jdbc:mysql://localhost:3306/taskmanager_db?...` |
| `DB_USERNAME` | Usuario de la base de datos | `root` |
| `DB_PASSWORD` | Contraseña de la base de datos | *(vacío)* |
| `JWT_SECRET` | Clave secreta Base64 para firmar los JWT (HS256, ≥256 bits) | clave de desarrollo incluida — **cámbiala en producción** |
| `JWT_EXPIRATION` | Tiempo de vida del token en milisegundos | `3600000` (1 hora) |

Para definirlas en Windows (PowerShell), antes de ejecutar la app:

```powershell
$env:DB_USERNAME = "tu_usuario"
$env:DB_PASSWORD = "tu_contraseña"
$env:JWT_SECRET  = "una-clave-secreta-larga-en-base64-solo-para-produccion"
```

En Linux/macOS (bash):

```bash
export DB_USERNAME=tu_usuario
export DB_PASSWORD=tu_contraseña
export JWT_SECRET=una-clave-secreta-larga-en-base64-solo-para-produccion
```

También puedes configurarlas como variables de entorno de ejecución en tu IDE (IntelliJ/Eclipse/VS Code).

### 5. Compilar y ejecutar

```bash
mvn spring-boot:run
```

La aplicación estará disponible en: `http://localhost:8081`

## Cuentas de prueba

| Usuario | Contraseña | Rol |
|---|---|---|
| `admin` | `admin123` | ADMIN |
| `usuario` | `user123` | USER |

## Estructura del Proyecto

```
task-manager/
├── database/
│   ├── schema.sql                        # Creación de BD y tablas
│   └── data.sql                          # Datos iniciales (admin/usuario)
├── src/main/
│   ├── java/com/prymevolution/taskmanager/
│   │   ├── config/
│   │   │   ├── SecurityConfig.java       # Spring Security: JWT stateless, roles, CSRF
│   │   │   └── DataInitializer.java      # Siembra de datos de prueba (alternativa a data.sql)
│   │   ├── security/
│   │   │   ├── JwtUtil.java              # Generación/validación de tokens
│   │   │   ├── JwtAuthenticationFilter.java
│   │   │   ├── RestAwareAuthenticationEntryPoint.java
│   │   │   └── RestAwareAccessDeniedHandler.java
│   │   ├── controller/
│   │   │   ├── HomeController.java       # GET /
│   │   │   ├── TaskController.java       # CRUD /tasks
│   │   │   ├── AuthController.java       # /login, /register, /logout (cookie JWT)
│   │   │   ├── AdminController.java      # /admin (solo ADMIN)
│   │   │   └── api/
│   │   │       └── AuthRestController.java  # POST /api/auth/login (JSON)
│   │   ├── dto/
│   │   │   ├── LoginRequest.java
│   │   │   └── JwtResponse.java
│   │   ├── entity/
│   │   │   ├── Task.java
│   │   │   ├── User.java
│   │   │   ├── TaskStatus.java
│   │   │   ├── TaskPriority.java
│   │   │   └── UserRole.java
│   │   ├── repository/
│   │   │   ├── TaskRepository.java
│   │   │   └── UserRepository.java
│   │   ├── service/
│   │   │   ├── TaskService.java
│   │   │   ├── UserService.java
│   │   │   ├── AuthService.java          # Autenticación + emisión de JWT
│   │   │   └── UserDetailsServiceImpl.java
│   │   └── validator/
│   │       └── TaskValidator.java
│   └── resources/
│       ├── templates/                    # Thymeleaf (fragments, auth, tasks, admin, error)
│       ├── static/css/styles.css
│       └── application.properties
```

## Rutas de la Aplicación

| Ruta | Método | Acceso | Descripción |
|---|---|---|---|
| `/` | GET | Público | Página de inicio |
| `/login` | GET | Público | Formulario de login |
| `/login` | POST | Público | Autentica y guarda el JWT en cookie `HttpOnly` |
| `/register` | GET/POST | Público | Registro de nuevos usuarios |
| `/logout` | POST | Autenticado | Elimina la cookie JWT |
| `/api/auth/login` | POST | Público | API REST: devuelve el JWT en JSON |
| `/tasks` | GET | USER/ADMIN | Listar tareas propias |
| `/tasks/nueva` | GET/POST | USER/ADMIN | Crear tarea |
| `/tasks/editar/{id}` | GET/POST | Dueño de la tarea | Editar tarea |
| `/tasks/eliminar/{id}` | POST | Dueño de la tarea | Eliminar tarea |
| `/admin` | GET | ADMIN | Panel de administración |
| `/admin/usuarios/{id}/eliminar` | POST | ADMIN | Eliminar usuario |
| `/admin/tasks/{id}/eliminar` | POST | ADMIN | Eliminar cualquier tarea |

## Patrones y decisiones de diseño

- **MVC**: separación de controladores, servicios y vistas.
- **Repository Pattern**: Spring Data JPA.
- **Autenticación sin estado (stateless)**: sin `HttpSession`; el `SecurityContext` se reconstruye en cada petición a partir del JWT.
- **JWT en cookie `HttpOnly` para el navegador**: evita exponer el token a JavaScript (mitiga XSS) sin sacrificar la experiencia de usuario de una app renderizada en servidor.
- **API REST desacoplada de la vista**: `AuthRestController` permite integrar la autenticación con clientes externos sin depender de cookies.
- **Defensa en profundidad en autorización**: reglas HTTP en `SecurityConfig` + `@PreAuthorize` a nivel de método + verificación de propiedad del recurso en el controlador.
- **BCrypt**: almacenamiento seguro de contraseñas, con factor de coste 10.
- **Configuración externalizada**: credenciales de BD y clave JWT fuera del código fuente, vía variables de entorno.
# Taskmanager
# Taskmanager
# Taskmanager
