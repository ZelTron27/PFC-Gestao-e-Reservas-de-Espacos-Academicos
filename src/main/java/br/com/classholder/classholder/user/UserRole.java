package br.com.classholder.classholder.user;

public enum UserRole {
    ADMIN,
    COORDENACAO,
    PROFESSOR;

    public String getAuthority() {
        return "ROLE_" + name();
    }

    public boolean canManage(UserRole target) {
        return this != PROFESSOR && target != ADMIN;
    }
}
