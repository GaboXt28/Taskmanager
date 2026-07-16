package com.prymevolution.taskmanager.validator;

import com.prymevolution.taskmanager.entity.Task;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

import java.time.LocalDate;

/**
 * Validador personalizado para la entidad Task.
 *
 * ¿Por qué un validador personalizado además de las anotaciones (@NotBlank, @NotNull, etc.)?
 *
 * Las anotaciones de Jakarta Bean Validation validan condiciones simples que se pueden
 * expresar declarativamente: campo vacío, longitud, formato, etc.
 * Sin embargo, algunas reglas requieren lógica más compleja que no cabe en una anotación:
 *   - Verificar que una fecha no sea pasada en el contexto de edición
 *   - Reglas que dependen de múltiples campos a la vez
 *   - Mensajes de error con información dinámica
 *
 * Patrón: Spring Validator Interface.
 * Al implementar org.springframework.validation.Validator, Spring puede invocar este
 * validador manualmente desde el controlador con: validator.validate(task, bindingResult)
 * Los errores se acumulan en el mismo BindingResult que las anotaciones, así Thymeleaf
 * los muestra todos juntos en el formulario con th:errors="*{campo}".
 *
 * Flujo de validación en TaskController:
 *   1. @Valid dispara las anotaciones: @NotBlank, @Size, @FutureOrPresent, etc.
 *   2. validator.validate(task, result) aplica las reglas de este validador
 *   3. if (result.hasErrors()) → se vuelve al formulario con todos los errores combinados
 *
 * @Component → registra esta clase como bean de Spring para que pueda inyectarse
 *   en TaskController con @RequiredArgsConstructor.
 */
@Component
public class TaskValidator implements Validator {

    /**
     * Declara para qué tipo de objeto aplica este validador.
     *
     * Spring llama a este método antes de validate() para verificar compatibilidad.
     * Si devuelve false para un tipo, Spring lanza IllegalArgumentException.
     * Aquí decimos: "este validador solo aplica a objetos de tipo Task".
     *
     * @param clazz la clase del objeto que se quiere validar
     * @return true si este validador puede manejar ese tipo de clase
     */
    @Override
    public boolean supports(Class<?> clazz) {
        return Task.class.equals(clazz);
    }

    /**
     * Ejecuta las reglas de validación de negocio sobre el objeto Task.
     *
     * @param target el objeto a validar (viene como Object, hacemos cast a Task)
     * @param errors acumulador de errores — si llamamos a errors.rejectValue(),
     *               el error queda registrado y result.hasErrors() devuelve true en el controller.
     *
     * errors.rejectValue(campo, codigoError, mensajeUsuario):
     *   - campo        → nombre del campo Java en Task (debe coincidir con th:field en el HTML)
     *   - codigoError  → código interno de Spring para internacionalización (i18n), aquí no la usamos
     *   - mensaje      → texto que verá el usuario en el formulario
     */
    @Override
    public void validate(Object target, Errors errors) {
        Task t = (Task) target;

        // --- Validación del título ---
        // Aunque @NotBlank en Task.java ya lo valida, aquí lo re-chequeamos como respaldo
        // para el caso en que el validador se use independientemente de las anotaciones.
        if (t.getTitle() == null || t.getTitle().isBlank())
            errors.rejectValue("title", "title.empty", "El título no puede estar vacío");

        // --- Validación de la fecha de vencimiento ---
        // Verificamos dos condiciones separadas para dar mensajes de error específicos:
        //   1. Que la fecha no sea null (no se seleccionó ninguna)
        //   2. Que la fecha no sea anterior a hoy (fecha en el pasado)
        //
        // isBefore(LocalDate.now()) es estricto: hoy mismo es válido (no es "antes de hoy")
        // Esto es consistente con la anotación @FutureOrPresent de la entidad.
        if (t.getDueDate() == null)
            errors.rejectValue("dueDate", "dueDate.null", "La fecha de vencimiento es obligatoria");
        else if (t.getDueDate().isBefore(LocalDate.now()))
            errors.rejectValue("dueDate", "dueDate.past", "La fecha debe ser actual o futura");

        // --- Validación de la prioridad ---
        // El usuario debe seleccionar explícitamente una prioridad del select.
        // Si no selecciona, el valor llega null desde el formulario.
        if (t.getPriority() == null)
            errors.rejectValue("priority", "priority.null", "Debe seleccionar una prioridad");
    }
}
