package br.com.classholder.classholder.user.dto;

import br.com.classholder.classholder.user.UserRole;

public record UserResponse(Long id, String name, String email, UserRole role, boolean active) {

    public UserResponse(Long id, String name, String email, UserRole role) {
        this(id, name, email, role, true);
    }

}
