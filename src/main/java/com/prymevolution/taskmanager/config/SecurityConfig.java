package com.prymevolution.taskmanager.config;

import com.prymevolution.taskmanager.security.JwtAuthenticationFilter;
import com.prymevolution.taskmanager.security.RestAwareAccessDeniedHandler;
import com.prymevolution.taskmanager.security.RestAwareAuthenticationEntryPoint;
import com.prymevolution.taskmanager.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuración central de Spring Security — arquitectura JWT sin estado (stateless).
 *
 * Esta clase define:
 *   1. Qué rutas son públicas y cuáles requieren autenticación o roles específicos
 *   2. Que el servidor NO crea ni usa HttpSession (SessionCreationPolicy.STATELESS):
 *      cada petición se autentica desde cero a partir del JWT que trae (cabecera o cookie)
 *   3. CSRF deshabilitado: la protección CSRF clásica de Spring existe para defender sesiones
 *      basadas en cookies de sesión; al no haber sesión de servidor, y al no confiar en cookies
 *      de sesión para autenticar, ese vector de ataque no aplica de la misma forma. La cookie
 *      "jwt" es HttpOnly + SameSite=Strict, lo que ya mitiga su uso indebido desde otro sitio.
 *   4. El filtro JwtAuthenticationFilter se registra ANTES del filtro estándar de usuario/contraseña,
 *      de modo que reconstruye el SecurityContext en cada petición antes de que se evalúen
 *      las reglas de autorización de abajo.
 *   5. Cómo se encriptan las contraseñas (BCrypt) y cómo Spring Security consulta la BD.
 *
 * --- Anotaciones de clase ---
 *
 * @EnableWebSecurity → activa la infraestructura de filtros HTTP de Spring Security.
 *
 * @EnableMethodSecurity → habilita las anotaciones @PreAuthorize/@PostAuthorize a nivel de método
 *   (ej. AdminController usa @PreAuthorize("hasRole('ADMIN')")). Sin esta anotación esas
 *   anotaciones se ignoran silenciosamente y solo quedaría la protección por SecurityFilterChain.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAwareAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAwareAccessDeniedHandler accessDeniedHandler;

    /**
     * Define la cadena de filtros de seguridad — el corazón de la configuración.
     *
     * @param http objeto de construcción que permite configurar la seguridad HTTP
     * @return la cadena de filtros configurada
     * @throws Exception si hay algún error de configuración
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // =====================================================================
            // 1. CSRF — deshabilitado: la app es stateless y se autentica por token, no por
            //    cookie de sesión, que es el vector que la protección CSRF de Spring mitiga.
            // =====================================================================
            .csrf(csrf -> csrf.disable())

            // =====================================================================
            // 2. GESTIÓN DE SESIÓN — STATELESS: Spring Security nunca crea ni lee una HttpSession.
            //    Toda la información de autenticación viaja en el JWT de cada petición.
            // =====================================================================
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // =====================================================================
            // 3. AUTORIZACIÓN DE RUTAS (quién puede acceder a qué)
            // =====================================================================
            .authorizeHttpRequests(auth -> auth
                // Rutas PÚBLICAS — accesibles sin token JWT:
                //   /, /login, /register    → páginas Thymeleaf de acceso público
                //   /css/**, /js/**, /error → estáticos y manejo de errores
                //   /api/auth/**            → login/registro de la API REST (hay que poder
                //                              pedir el token antes de tenerlo)
                .requestMatchers("/", "/login", "/register", "/css/**", "/js/**", "/error", "/api/auth/**")
                    .permitAll()

                // Rutas de ADMINISTRACIÓN — solo usuarios con autoridad ROLE_ADMIN
                .requestMatchers("/admin/**").hasRole("ADMIN")

                // Rutas de TAREAS — cualquier usuario autenticado (USER o ADMIN)
                .requestMatchers("/tasks/**").authenticated()

                // Cualquier otra ruta no especificada también requiere JWT válido (fail-safe)
                .anyRequest().authenticated()
            )

            // =====================================================================
            // 4. MANEJO DE ERRORES DE AUTENTICACIÓN/AUTORIZACIÓN
            //    (reemplaza a .formLogin()/.exceptionHandling().accessDeniedPage() de la
            //    versión basada en sesión: ahora no hay un formulario que Spring Security
            //    procese automáticamente, así que estos handlers deciden qué responder)
            // =====================================================================
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )

            // Registra el filtro JWT antes del filtro estándar de usuario/contraseña de Spring Security
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            .authenticationProvider(authenticationProvider());

        return http.build();
    }

    /**
     * Bean que define el algoritmo de encriptación de contraseñas: BCrypt.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Conecta Spring Security con nuestra capa de datos (UserDetailsServiceImpl + BCrypt).
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * Expone el AuthenticationManager como bean para que AuthService pueda invocar
     * authenticate(username, password) manualmente durante el login (ya no existe
     * el filtro automático de formLogin que lo hacía por nosotros).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
