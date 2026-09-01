package br.com.classholder.classholder.modules.user.domain;

public enum UserRole {
    ADMIN,
    COORDENACAO,
    PROFESSOR,
    ALUNO;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}