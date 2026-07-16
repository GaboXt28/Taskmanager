package com.prymevolution.taskmanager.controller;

import com.prymevolution.taskmanager.entity.User;
import com.prymevolution.taskmanager.service.AuthService;
import com.prymevolution.taskmanager.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;

/**
 * Controlador de autenticación — maneja el formulario de registro de usuarios.
 *
 * Patrón aplicado: Controller (capa de presentación del modelo MVC).
 *
 * Responsabilidades de este controlador:
 *   1. GET /login    → muestra el formulario de login (el POST lo maneja Spring Security)
 *   2. GET /register → muestra el formulario de registro vacío
 *   3. POST /register → procesa el registro: valida, verifica duplicados y crea la cuenta
 *
 * Autenticación JWT (arquitectura stateless):
 *   Desde que SecurityConfig ya no usa .formLogin(), el POST /login SÍ vive en esta clase.
 *   A diferencia de la versión anterior (donde Spring Security procesaba el formulario y
 *   creaba una HttpSession), ahora este controlador:
 *     a) Delega en AuthService.login(), que usa AuthenticationManager para validar credenciales
 *        (mismo UserDetailsServiceImpl + BCrypt de siempre) y genera un JWT con JwtUtil.
 *     b) Guarda ese JWT en una cookie HttpOnly llamada "jwt".
 *     c) Redirige a /tasks (éxito) o a /login?error (fallo).
 *   El navegador reenvía la cookie automáticamente en cada petición posterior, y
 *   JwtAuthenticationFilter la lee para reconstruir el SecurityContext — sin sesión de servidor.
 *
 *   Para clientes de API (Postman, un frontend SPA, etc.) que prefieran manejar el token
 *   ellos mismos en vez de depender de cookies, existe además AuthRestController en
 *   POST /api/auth/login, que devuelve el JWT como JSON puro.
 *
 * @Controller → controlador MVC de Spring.
 * @RequiredArgsConstructor → Lombok inyecta las dependencias vía constructor.
 */
@Controller
@RequiredArgsConstructor
public class AuthController {

    /** Nombre de la cookie HttpOnly donde se guarda el JWT del usuario logueado en el navegador. */
    private static final String JWT_COOKIE_NAME = "jwt";

    /** Service para verificar duplicados y registrar nuevos usuarios. */
    private final UserService userService;

    /** Autentica credenciales y emite el JWT correspondiente (compartido con AuthRestController). */
    private final AuthService authService;

    /**
     * Muestra el formulario de inicio de sesión.
     *
     * @GetMapping("/login") → responde a GET /login
     *
     * Spring Security pasa parámetros en la URL para comunicar el resultado de acciones:
     *   ?error  → las credenciales fueron incorrectas (username no existe o password incorrecto)
     *   ?logout → el usuario acaba de cerrar sesión exitosamente
     *
     * @RequestParam(required = false) → el parámetro es opcional; si no está en la URL, llega null.
     *   Esto permite que el mismo método maneje GET /login, GET /login?error y GET /login?logout.
     *
     * @param error  presente en la URL si el login falló (Spring Security lo agrega automáticamente)
     * @param logout presente en la URL si el usuario acaba de hacer logout
     * @param model  para enviar mensajes al template
     * @return template del formulario de login: "auth/login" → templates/auth/login.html
     */
    @GetMapping("/login")
    public String loginForm(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {

        // Si el parámetro ?error está presente (no null), las credenciales fueron incorrectas
        if (error != null) {
            model.addAttribute("errorMsg", "Usuario o contraseña incorrectos.");
        }

        // Si el parámetro ?logout está presente, el usuario cerró sesión correctamente
        if (logout != null) {
            model.addAttribute("logoutMsg", "Has cerrado sesión correctamente.");
        }

        return "auth/login";  // → templates/auth/login.html
    }

    /**
     * Procesa el envío del formulario de login (POST /login).
     *
     * Sustituye al .formLogin().loginProcessingUrl("/login") que antes manejaba Spring Security
     * automáticamente. Ahora este método:
     *   1. Delega la validación de credenciales en AuthService (AuthenticationManager + BCrypt).
     *   2. Si son correctas, genera el JWT y lo guarda en una cookie HttpOnly.
     *   3. Redirige a /tasks. Si fallan, redirige a /login?error (mismo comportamiento visible
     *      que tenía la versión basada en sesión, para no romper la experiencia del usuario).
     *
     * @param username        usuario ingresado en el formulario
     * @param password        contraseña ingresada en el formulario
     * @param response        para adjuntar la cookie "jwt" a la respuesta HTTP
     * @return redirect a /tasks (éxito) o /login?error (credenciales inválidas)
     */
    @PostMapping("/login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletResponse response) {
        try {
            AuthService.AuthResult result = authService.login(username, password);
            response.addHeader("Set-Cookie", buildJwtCookie(result.token(), Duration.ofHours(1)).toString());
            return "redirect:/tasks";
        } catch (AuthenticationException e) {
            // BadCredentialsException (usuario/contraseña incorrectos) o DisabledException (cuenta suspendida)
            return "redirect:/login?error";
        }
    }

    /**
     * Cierra la sesión del usuario eliminando la cookie "jwt" del navegador.
     *
     * Como la aplicación es stateless, "cerrar sesión" no significa invalidar nada en el
     * servidor (no existe una HttpSession que destruir): basta con que el navegador deje de
     * enviar el token. Sobrescribimos la cookie con Max-Age=0 para que el navegador la borre.
     *
     * @return redirect a /login?logout, igual que el comportamiento previo basado en sesión
     */
    @PostMapping("/logout")
    public String logout(HttpServletResponse response) {
        response.addHeader("Set-Cookie", buildJwtCookie("", Duration.ZERO).toString());
        return "redirect:/login?logout";
    }

    /**
     * Construye la cookie HttpOnly que transporta el JWT en el navegador.
     *
     * HttpOnly     → JavaScript del cliente no puede leer la cookie (mitiga robo por XSS).
     * SameSite=Lax → no se envía en peticiones cross-site iniciadas por otros orígenes (mitiga CSRF).
     * Secure       → deshabilitado aquí porque el entorno de desarrollo corre en HTTP; en un
     *                despliegue real sobre HTTPS debe activarse (.secure(true)).
     *
     * @param value    el JWT (o "" para expirar la cookie en el logout)
     * @param maxAge   tiempo de vida de la cookie; Duration.ZERO la elimina inmediatamente
     */
    private ResponseCookie buildJwtCookie(String value, Duration maxAge) {
        return ResponseCookie.from(JWT_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .sameSite("Lax")
                .maxAge(maxAge)
                .build();
    }

    /**
     * Muestra el formulario de registro vacío.
     *
     * Se agrega un objeto User vacío (new User()) al modelo para que Thymeleaf
     * pueda enlazarlo con el formulario usando th:object="${user}".
     * Esto permite que th:field="*{username}" funcione correctamente y que,
     * si el formulario falla la validación, los valores ingresados se conserven en el form.
     *
     * @return template del formulario de registro: "auth/register" → templates/auth/register.html
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());  // objeto vacío para el data binding del formulario
        return "auth/register";
    }

    /**
     * Procesa el formulario de registro de un nuevo usuario.
     *
     * Flujo de validación en dos etapas:
     *   ETAPA 1 — Anotaciones de entidad (@Valid):
     *     @NotBlank, @Size, @Email se evalúan automáticamente.
     *     Los errores se acumulan en BindingResult.
     *
     *   ETAPA 2 — Validaciones de negocio (código explícito):
     *     a) Username ya registrado → no se permiten duplicados
     *     b) Email ya registrado    → no se permiten duplicados
     *     c) Contraseñas no coinciden → el campo confirmPassword no es parte de la entidad User,
     *        por eso no puede validarse con anotaciones y se verifica manualmente
     *     d) Contraseña demasiado corta → mínimo 6 caracteres
     *
     * Si CUALQUIER validación falla, se vuelve al formulario con TODOS los errores visibles.
     * Esto evita que el usuario tenga que corregir de a un error por vez.
     *
     * @Valid → activa las validaciones de anotaciones de la entidad User
     *
     * @ModelAttribute("user") → enlaza los campos del formulario HTML con el objeto User.
     *   Spring convierte automáticamente los parámetros del POST a los tipos correctos de cada campo.
     *
     * BindingResult → acumulador de errores. DEBE ir inmediatamente después del @ModelAttribute
     *   que se valida. Si hay un espacio entre ellos con otro parámetro, Spring lanza excepción.
     *
     * @RequestParam("confirmPassword") → campo extra del formulario que no existe en la entidad User.
     *   Se captura directamente como String para compararlo con user.getPassword().
     *
     * RedirectAttributes → para enviar el mensaje de éxito al login después del redirect.
     *   addFlashAttribute() guarda el dato en sesión HTTP y se consume una sola vez.
     *
     * @return si hay errores → vuelve al formulario; si todo OK → redirect al login
     */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("user") User user,
                           BindingResult result,
                           @RequestParam("confirmPassword") String confirmPassword,
                           RedirectAttributes redirectAttributes,
                           Model model) {

        // --- Validaciones de negocio (no cubiertas por anotaciones) ---

        // Verificar que el username no esté tomado — feedback preventivo antes del INSERT
        // Más amigable que dejar que MySQL lance una excepción de constraint UNIQUE
        if (userService.existsByUsername(user.getUsername())) {
            // rejectValue(campo, codigoError, mensajeUsuario) → agrega el error al BindingResult
            // "campo" debe coincidir exactamente con el nombre del campo en la entidad User
            result.rejectValue("username", "username.exists", "El nombre de usuario ya está en uso");
        }

        // Verificar que el email no esté registrado ya
        if (userService.existsByEmail(user.getEmail())) {
            result.rejectValue("email", "email.exists", "El email ya está registrado");
        }

        // Verificar que las dos contraseñas ingresadas sean idénticas
        // user.getPassword() es la del campo "password", confirmPassword es el campo "confirmPassword"
        if (!user.getPassword().equals(confirmPassword)) {
            result.rejectValue("password", "password.mismatch", "Las contraseñas no coinciden");
        }

        // Validar longitud mínima de la contraseña (la validación @Size está en User pero
        // aquí damos un mensaje más claro y en español)
        if (user.getPassword().length() < 6) {
            result.rejectValue("password", "password.short", "La contraseña debe tener al menos 6 caracteres");
        }

        // Si hay CUALQUIER error (de @Valid o de los rejectValue() de arriba), volvemos al form
        // Thymeleaf mostrará todos los mensajes de error con th:errors="*{campo}"
        if (result.hasErrors()) {
            return "auth/register";  // el objeto "user" con los errores sigue en el Model
        }

        // Todo válido: registramos el usuario (UserService encripta la contraseña y asigna rol USER)
        userService.register(user);

        // Mensaje flash: sobrevive el redirect y se muestra una sola vez en la página de login
        // addFlashAttribute() vs model.addAttribute(): el flash sobrevive redirects, el model no
        redirectAttributes.addFlashAttribute("successMsg", "¡Cuenta creada! Ahora puedes iniciar sesión.");

        // PRG Pattern: redirect al login para evitar doble registro si el usuario pulsa F5
        return "redirect:/login";
    }
}
