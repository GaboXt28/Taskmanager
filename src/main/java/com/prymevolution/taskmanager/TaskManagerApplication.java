package com.prymevolution.taskmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Clase principal de la aplicación — punto de entrada de Spring Boot.
 *
 * @SpringBootApplication es una "meta-anotación" que agrupa tres anotaciones en una sola:
 *   - @Configuration       → marca esta clase como fuente de beans de Spring (configuración Java)
 *   - @EnableAutoConfiguration → activa la auto-configuración: Spring Boot detecta las dependencias
 *                                del pom.xml (Thymeleaf, JPA, Security, etc.) y las configura solas
 *   - @ComponentScan       → le dice a Spring que escanee este paquete y todos los sub-paquetes
 *                             buscando clases anotadas con @Controller, @Service, @Repository, etc.
 *
 * Gracias a estas tres anotaciones juntas, basta con ejecutar el main para tener:
 *   - Servidor Tomcat embebido escuchando en el puerto 8081
 *   - Conexión a MySQL configurada desde application.properties
 *   - Spring Security activo con todas sus reglas
 *   - Hibernate gestionando el esquema de la base de datos automáticamente
 */
@SpringBootApplication
public class TaskManagerApplication {

    /**
     * Método main estándar de Java — aquí comienza la ejecución.
     * SpringApplication.run() bootstrapea todo el contexto de Spring:
     *   1. Carga application.properties
     *   2. Crea todos los beans (controllers, services, repositories, etc.)
     *   3. Ejecuta los CommandLineRunner (como DataInitializer) para sembrar datos iniciales
     *   4. Arranca el servidor Tomcat embebido y queda escuchando peticiones HTTP
     *
     * @param args argumentos de línea de comandos (no usamos ninguno en este proyecto)
     */
    public static void main(String[] args) {
        SpringApplication.run(TaskManagerApplication.class, args);
    }
}
