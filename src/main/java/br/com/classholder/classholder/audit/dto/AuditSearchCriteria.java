package br.com.classholder.classholder.audit.dto;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public record AuditSearchCriteria(
        Instant from,
        Instant to,
        Long userId,
        String action,
        List<EntityRef> entities) {

    public record EntityRef(String type, Collection<Long> ids) {
    }

    public static AuditSearchCriteria none() {
        return new AuditSearchCriteria(null, null, null, null, null);
    }

}
