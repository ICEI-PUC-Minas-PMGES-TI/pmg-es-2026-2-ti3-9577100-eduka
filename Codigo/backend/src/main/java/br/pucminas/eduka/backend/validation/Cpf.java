package br.pucminas.eduka.backend.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * CPF com 11 digitos, sem mascara, e digitos verificadores corretos. Nulo e
 * considerado valido: obrigatoriedade fica com {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = CpfValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface Cpf {

    String message() default "cpf inválido";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
