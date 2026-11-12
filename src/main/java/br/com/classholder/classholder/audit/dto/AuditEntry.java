package br.com.classholder.classholder.audit.dto;

import java.time.Instant;

public record AuditEntry(
        Long id,
        Long userId,
        String action,
        String entityType,
        Long entityId,
        String entityName,
        Instant timestamp,
        String details) {

    public static final String LOGIN_FAILURE_ACTION = "ACESSO_LOGIN_FALHA";
    public static final String USER_ENTITY_TYPE = "USUARIO";

}
