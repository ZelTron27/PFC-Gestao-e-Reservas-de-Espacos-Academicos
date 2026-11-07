package br.com.classholder.classholder.audit.dto;

import java.util.List;

public record AuditPage(List<AuditEntry> entries, long total, int page, int size) {

    public int totalPages() {
        return total == 0 ? 1 : (int) ((total + size - 1) / size);
    }

}
