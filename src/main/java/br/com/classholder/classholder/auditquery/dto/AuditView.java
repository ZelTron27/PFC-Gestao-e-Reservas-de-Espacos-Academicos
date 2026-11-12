package br.com.classholder.classholder.auditquery.dto;

import java.time.LocalDate;
import java.util.List;

public record AuditView(
        LocalDate from,
        LocalDate to,
        String periodError,
        String selectedUser,
        Long selectedRoom,
        String selectedAction,
        List<Option> users,
        List<Option> rooms,
        List<Option> actions,
        List<Row> rows,
        long total,
        int page,
        int totalPages,
        String baseQuery) {

    public record Option(String value, String label) {
    }

    public record Row(String timestamp, String author, String action, String item, String details) {
    }

}
