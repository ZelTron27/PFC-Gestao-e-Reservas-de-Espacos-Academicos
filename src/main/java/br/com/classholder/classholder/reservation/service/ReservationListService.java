package br.com.classholder.classholder.reservation.service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import br.com.classholder.classholder.reservation.dto.PendingListView;
import br.com.classholder.classholder.reservation.dto.PendingRequestGroup;
import br.com.classholder.classholder.reservation.dto.ReservationListView;
import br.com.classholder.classholder.reservation.dto.ReservationResponse;
import br.com.classholder.classholder.shared.ListFilters;
import br.com.classholder.classholder.shared.ListPage;
import br.com.classholder.classholder.shared.Option;

@Service
public class ReservationListService {

    private static final int PENDING_PAGE_SIZE = 10;
    private static final String FINISHED = "FINALIZADA";
    private static final String PERIOD_ERROR = "A data final não pode ser anterior à data inicial.";

    private static final List<Option> STATUS_OPTIONS = List.of(
            new Option("CONFIRMADA", "Confirmada"),
            new Option(FINISHED, "Finalizada"),
            new Option("PENDENTE", "Aguardando avaliação"),
            new Option("CANCELADA", "Cancelada"),
            new Option("RECUSADA", "Recusada"),
            new Option("EXPIRADA", "Expirada"));

    private static final Set<String> STATUS_VALUES = Set.of("CONFIRMADA", FINISHED, "PENDENTE", "CANCELADA",
            "RECUSADA", "EXPIRADA");

    public ReservationListView buildReservations(List<ReservationResponse> all, LocalDate requestedFrom,
            LocalDate requestedTo, Long roomId, String responsible, String status, int page) {
        boolean invalidPeriod = isInvalidPeriod(requestedFrom, requestedTo);
        LocalDate from = invalidPeriod ? null : requestedFrom;
        LocalDate to = invalidPeriod ? null : requestedTo;
        String selectedResponsible = ListFilters.clean(responsible);
        String cleanedStatus = ListFilters.clean(status);
        String selectedStatus = cleanedStatus != null && STATUS_VALUES.contains(cleanedStatus) ? cleanedStatus : null;

        List<ReservationResponse> matched = all.stream()
                .filter(reservation -> inPeriod(reservation.date(), from, to))
                .filter(reservation -> roomId == null || roomId.equals(reservation.roomId()))
                .filter(reservation -> selectedResponsible == null
                        || selectedResponsible.equals(reservation.responsibleName()))
                .filter(reservation -> selectedStatus == null || selectedStatus.equals(statusKey(reservation)))
                .toList();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("de", from);
        params.put("ate", to);
        params.put("sala", roomId);
        params.put("responsavel", selectedResponsible);
        params.put("status", selectedStatus);

        return new ReservationListView(ListPage.of(matched, page, params), roomOptions(all),
                responsibleOptions(all), STATUS_OPTIONS, from, to, roomId == null ? null : roomId.toString(),
                selectedResponsible, selectedStatus, invalidPeriod ? PERIOD_ERROR : null);
    }

    public PendingListView buildPending(List<PendingRequestGroup> all, LocalDate requestedFrom,
            LocalDate requestedTo, Long roomId, String responsible, int page) {
        boolean invalidPeriod = isInvalidPeriod(requestedFrom, requestedTo);
        LocalDate from = invalidPeriod ? null : requestedFrom;
        LocalDate to = invalidPeriod ? null : requestedTo;
        String selectedResponsible = ListFilters.clean(responsible);

        List<PendingRequestGroup> matched = all.stream()
                .filter(group -> inPeriod(group.date(), from, to))
                .filter(group -> roomId == null || roomId.equals(group.roomId()))
                .map(group -> withResponsible(group, selectedResponsible))
                .filter(group -> !group.requests().isEmpty())
                .toList();

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("de", from);
        params.put("ate", to);
        params.put("sala", roomId);
        params.put("responsavel", selectedResponsible);

        List<ReservationResponse> everyRequest = all.stream().flatMap(group -> group.requests().stream()).toList();
        List<Option> rooms = all.stream()
                .collect(Collectors.toMap(PendingRequestGroup::roomId,
                        PendingRequestGroup::roomName, (first, second) -> first))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new Option(entry.getKey().toString(), entry.getValue()))
                .toList();

        return new PendingListView(ListPage.of(matched, page, PENDING_PAGE_SIZE, params), rooms,
                responsibleOptions(everyRequest), from, to, roomId == null ? null : roomId.toString(),
                selectedResponsible, invalidPeriod ? PERIOD_ERROR : null);
    }

    private PendingRequestGroup withResponsible(PendingRequestGroup group, String responsible) {
        if (responsible == null) {
            return group;
        }
        List<ReservationResponse> requests = group.requests().stream()
                .filter(request -> responsible.equals(request.responsibleName()))
                .toList();
        return new PendingRequestGroup(group.roomId(), group.roomName(), group.date(), requests);
    }

    private boolean isInvalidPeriod(LocalDate from, LocalDate to) {
        return from != null && to != null && to.isBefore(from);
    }

    private boolean inPeriod(LocalDate date, LocalDate from, LocalDate to) {
        return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private String statusKey(ReservationResponse reservation) {
        return reservation.finished() ? FINISHED : reservation.status().name();
    }

    private List<Option> roomOptions(List<ReservationResponse> reservations) {
        Map<Long, String> names = new LinkedHashMap<>();
        reservations.forEach(reservation -> names.putIfAbsent(reservation.roomId(), reservation.roomName()));
        return names.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new Option(entry.getKey().toString(), entry.getValue()))
                .toList();
    }

    private List<String> responsibleOptions(List<ReservationResponse> reservations) {
        return reservations.stream()
                .map(ReservationResponse::responsibleName)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

}
