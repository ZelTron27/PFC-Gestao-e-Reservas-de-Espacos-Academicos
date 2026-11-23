package br.com.classholder.classholder.reservation.dto;

import java.time.LocalDate;
import java.util.List;

import br.com.classholder.classholder.shared.ListPage;
import br.com.classholder.classholder.shared.Option;

public record ReservationListView(
        ListPage<ReservationResponse> page,
        List<Option> rooms,
        List<String> responsibles,
        List<Option> statuses,
        LocalDate from,
        LocalDate to,
        String selectedRoom,
        String selectedResponsible,
        String selectedStatus,
        String periodError) {
}
