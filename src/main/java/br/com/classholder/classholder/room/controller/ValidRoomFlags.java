package br.com.classholder.classholder.room.controller;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RoomFlagsValidator.class)
public @interface ValidRoomFlags {

    String message() default "Sala especial não pode permitir solicitação por aluno";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
