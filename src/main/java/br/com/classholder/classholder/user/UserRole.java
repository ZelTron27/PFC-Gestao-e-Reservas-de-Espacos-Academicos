package br.com.classholder.classholder.user;

public enum UserRole {
    ADMIN("Administrador"),
    COORDENACAO("Coordenação"),
    PROFESSOR("Professor");

    private final String label;

    UserRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getAuthority() {
        return "ROLE_" + name();
    }

    public boolean canManage(UserRole target) {
        return this != PROFESSOR && target != ADMIN;
    }

    public boolean canChangeStatusOf(UserRole target) {
        return canManage(target) || (this == ADMIN && target == ADMIN);
    }

    public boolean canAnonymize(UserRole target) {
        return this == ADMIN;
    }
}
