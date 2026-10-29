package br.com.classholder.classholder.user;

public enum UserRole {
    ADMIN,
    COORDENACAO,
    PROFESSOR;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}