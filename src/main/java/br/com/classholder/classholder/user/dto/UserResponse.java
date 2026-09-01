package br.com.classholder.classholder.user.dto;

import br.com.classholder.classholder.user.UserRole;

public record UserResponse(Long id, String name, String email, UserRole role) {
}
