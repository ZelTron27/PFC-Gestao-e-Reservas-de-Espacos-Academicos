package br.com.classholder.classholder.user.dto;

import br.com.classholder.classholder.user.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(

        @NotBlank(message = "Nome é obrigatório")
        String name,

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String password,

        @NotNull(message = "Perfil é obrigatório")
        UserRole role) {

}
