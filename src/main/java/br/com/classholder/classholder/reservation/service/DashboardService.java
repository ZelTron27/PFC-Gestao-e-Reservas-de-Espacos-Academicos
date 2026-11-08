package br.com.classholder.classholder.reservation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.dto.DashboardFormat;
import br.com.classholder.classholder.reservation.dto.DashboardResponse;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.CancellationShare;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RequestSummary;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomBar;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomComparison;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomOption;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomUsage;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.service.RoomService;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    public static final long MAX_PERIOD_DAYS = 366;

    private static final int CHECK_IN_WINDOW_MINUTES = 15;
    private static final int TOP_ROOMS = 3;
    private static final int MAX_COMPARED_ROOMS = 5;
    public static final int MAX_CHART_ROOMS = 10;
    private static final List<ReservationStatus> APPROVED_STATUSES = List.of(ReservationStatus.CONFIRMADA,
            ReservationStatus.CANCELADA);

    private final ReservationRepository reservationRepository;
    private final RoomService roomService;
    private final Clock clock;

    public DashboardService(ReservationRepository reservationRepository, RoomService roomService, Clock clock) {
        this.reservationRepository = reservationRepository;
        this.roomService = roomService;
        this.clock = clock;
    }

    public DashboardResponse buildDashboard(LocalDate requestedFrom, LocalDate requestedTo) {
        return buildDashboard(requestedFrom, requestedTo, null, false);
    }

    public DashboardResponse buildDashboard(LocalDate requestedFrom, LocalDate requestedTo,
            List<Long> requestedRoomIds) {
        return buildDashboard(requestedFrom, requestedTo, requestedRoomIds, false);
    }

    public DashboardResponse buildDashboard(LocalDate requestedFrom, LocalDate requestedTo,
            List<Long> requestedRoomIds, boolean allRooms) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate defaultFrom = now.toLocalDate().withDayOfMonth(1);
        LocalDate defaultTo = defaultFrom.plusMonths(1).minusDays(1);

        LocalDate from = requestedFrom != null ? requestedFrom : defaultFrom;
        LocalDate to = requestedTo != null ? requestedTo : defaultTo;
        String periodError = validatePeriod(from, to);
        if (periodError != null) {
            from = defaultFrom;
            to = defaultTo;
        }

        return compute(from, to, now, periodError, requestedRoomIds, allRooms);
    }

    private String validatePeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return "A data final não pode ser anterior à data inicial.";
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
            return "O período deve ter no máximo " + MAX_PERIOD_DAYS + " dias.";
        }
        return null;
    }

    private DashboardResponse compute(LocalDate from, LocalDate to, LocalDateTime now, String periodError,
            List<Long> requestedRoomIds, boolean allRooms) {
        long confirmed = reservationRepository.countByStatusAndDateBetween(ReservationStatus.CONFIRMADA, from, to);
        long finished = reservationRepository.countFinished(ReservationStatus.CONFIRMADA, from, to,
                now.toLocalDate(), now.toLocalTime());
        long pendingNow = reservationRepository.countByStatus(ReservationStatus.PENDENTE);

        LocalDateTime checkInLimit = now.minusMinutes(CHECK_IN_WINDOW_MINUTES);
        long missedCheckIn = reservationRepository.countByStatusAndCancellationReasonAndDateBetween(
                ReservationStatus.CANCELADA, CancellationReason.SEM_CHECKIN, from, to);
        long checkInWindowClosed = reservationRepository.countStartedUntil(ReservationStatus.CONFIRMADA, from, to,
                checkInLimit.toLocalDate(), checkInLimit.toLocalTime());

        long cancelled = reservationRepository.countByStatusAndDateBetween(ReservationStatus.CANCELADA, from, to);

        List<Object[]> usageRows = reservationRepository.summarizeUsageByRoom(ReservationStatus.CONFIRMADA, from, to);

        return new DashboardResponse(from, to, periodError, confirmed, finished, pendingNow,
                DashboardFormat.ratioPercent(missedCheckIn, missedCheckIn + checkInWindowClosed),
                DashboardFormat.ratioPercent(cancelled, cancelled + confirmed),
                cancellationShares(from, to), cancelled, roomUsage(usageRows), requestSummary(from, to),
                averageApprovalTime(from, to), roomComparison(from, to, usageRows, requestedRoomIds, allRooms));
    }

    private List<CancellationShare> cancellationShares(LocalDate from, LocalDate to) {
        List<Object[]> rows = reservationRepository.countCancelledByReason(ReservationStatus.CANCELADA, from, to);
        long total = rows.stream().mapToLong(row -> (Long) row[1]).sum();
        List<CancellationShare> shares = new ArrayList<>();
        for (Object[] row : rows) {
            long count = (Long) row[1];
            shares.add(new CancellationShare(((CancellationReason) row[0]).label(), count,
                    DashboardFormat.ratioPercent(count, total)));
        }
        return shares;
    }

    private List<RoomUsage> roomUsage(List<Object[]> rows) {
        List<Long> roomIds = rows.stream().map(row -> (Long) row[0]).toList();
        Map<Long, String> names = roomService.findRoomNames(roomIds);

        List<RoomUsage> usage = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            Object[] row = rows.get(index);
            Long roomId = (Long) row[0];
            long minutes = ((Number) row[2]).longValue();
            usage.add(new RoomUsage(roomId, names.get(roomId), (Long) row[1], minutes,
                    DashboardFormat.duration(minutes), index < TOP_ROOMS));
        }
        return usage;
    }

    private RequestSummary requestSummary(LocalDate from, LocalDate to) {
        long approved = reservationRepository.countApprovedRequests(APPROVED_STATUSES, from, to);
        long rejected = reservationRepository.countEndedRequests(ReservationStatus.RECUSADA, from, to);
        long expired = reservationRepository.countEndedRequests(ReservationStatus.EXPIRADA, from, to);
        long selfApproved = reservationRepository.countSelfApprovedRequests(APPROVED_STATUSES, from, to);
        long total = approved + rejected + expired;
        return new RequestSummary(approved, DashboardFormat.ratioPercent(approved, total), rejected,
                DashboardFormat.ratioPercent(rejected, total), expired,
                DashboardFormat.ratioPercent(expired, total), selfApproved, total);
    }

    private String averageApprovalTime(LocalDate from, LocalDate to) {
        Double seconds = reservationRepository.averageApprovalSeconds(APPROVED_STATUSES, from, to);
        if (seconds == null) {
            return DashboardFormat.NO_DATA;
        }
        return DashboardFormat.duration(Math.round(seconds / 60));
    }

    private RoomComparison roomComparison(LocalDate from, LocalDate to, List<Object[]> usageRows,
            List<Long> requestedRoomIds, boolean allRooms) {
        Map<Long, String> optionNames = new LinkedHashMap<>(roomService.findActiveRoomNames());
        List<Long> usedRoomIds = usageRows.stream().map(row -> (Long) row[0]).toList();
        Map<Long, String> usedNames = roomService.findRoomNames(usedRoomIds);
        usedRoomIds.forEach(id -> optionNames.putIfAbsent(id, usedNames.get(id)));

        List<Long> requested = requestedRoomIds == null || allRooms ? List.of()
                : requestedRoomIds.stream().distinct().toList();
        String error = null;
        if (requested.size() > MAX_COMPARED_ROOMS) {
            error = "Selecione no máximo " + MAX_COMPARED_ROOMS + " salas";
            requested = List.of();
        }
        List<Long> selected = requested.stream().filter(optionNames::containsKey).toList();

        Map<Long, Long> minutesByRoom = new LinkedHashMap<>();
        if (allRooms) {
            optionNames.keySet().forEach(id -> minutesByRoom.put(id, 0L));
            usageRows.forEach(row -> minutesByRoom.put((Long) row[0], ((Number) row[2]).longValue()));
        } else if (selected.isEmpty()) {
            usageRows.stream().limit(MAX_COMPARED_ROOMS)
                    .forEach(row -> minutesByRoom.put((Long) row[0], ((Number) row[2]).longValue()));
        } else {
            selected.forEach(id -> minutesByRoom.put(id, 0L));
            for (Object[] row : reservationRepository.summarizeUsageForRooms(ReservationStatus.CONFIRMADA, from, to,
                    selected)) {
                minutesByRoom.put((Long) row[0], ((Number) row[1]).longValue());
            }
        }

        List<RoomBar> bars = minutesByRoom.entrySet().stream()
                .map(entry -> new RoomBar(entry.getKey(), optionNames.get(entry.getKey()), entry.getValue(),
                        DashboardFormat.duration(entry.getValue())))
                .sorted(Comparator.comparingLong(RoomBar::minutes).reversed().thenComparing(RoomBar::label))
                .limit(MAX_CHART_ROOMS)
                .toList();

        List<RoomOption> options = optionNames.entrySet().stream()
                .map(entry -> new RoomOption(entry.getKey(), entry.getValue(), selected.contains(entry.getKey())))
                .sorted(Comparator.comparing(RoomOption::name))
                .toList();

        return new RoomComparison(bars, options, error, allRooms,
                comparisonSummary(bars.size(), optionNames.size(), allRooms, selected.size()));
    }

    private String comparisonSummary(int shown, int available, boolean allRooms, int selectedCount) {
        if (shown == 0) {
            return "Nenhuma sala para exibir";
        }
        if (allRooms) {
            if (available > MAX_CHART_ROOMS) {
                return "Exibindo as " + MAX_CHART_ROOMS + " salas com mais horas (todas)";
            }
            return shown == 1 ? "Exibindo a única sala" : "Exibindo todas as " + shown + " salas";
        }
        if (selectedCount > 0) {
            return "Exibindo " + selectedCount + (selectedCount == 1 ? " sala selecionada" : " salas selecionadas");
        }
        return shown == 1 ? "Exibindo a sala com mais horas" : "Exibindo as " + shown + " salas com mais horas";
    }

}
