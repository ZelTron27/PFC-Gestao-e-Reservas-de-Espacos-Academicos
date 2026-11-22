package br.com.classholder.classholder.reservation.dto;

import java.time.LocalDate;
import java.util.List;

import br.com.classholder.classholder.shared.ListPage;
import br.com.classholder.classholder.shared.Option;

public record PendingListView(
        ListPage<PendingRequestGroup> page,
        List<Option> rooms,
        List<String> responsibles,
        LocalDate from,
        LocalDate to,
        String selectedRoom,
        String selectedResponsible,
        String periodError) {
}
