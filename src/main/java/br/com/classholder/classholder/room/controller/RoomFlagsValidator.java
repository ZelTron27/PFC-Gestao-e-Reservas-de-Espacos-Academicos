package br.com.classholder.classholder.room.controller;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class RoomFlagsValidator implements ConstraintValidator<ValidRoomFlags, RoomFormData> {

    @Override
    public boolean isValid(RoomFormData form, ConstraintValidatorContext context) {
        if (!form.isSalaEspecial() || !form.isPermiteSolicitacaoAluno()) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("permiteSolicitacaoAluno")
                .addConstraintViolation();
        return false;
    }

}
