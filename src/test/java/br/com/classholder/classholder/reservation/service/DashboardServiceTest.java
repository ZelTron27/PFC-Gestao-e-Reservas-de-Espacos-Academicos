package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.dto.DashboardFormat;
import br.com.classholder.classholder.reservation.dto.DashboardResponse;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomBar;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomOption;
import br.com.classholder.classholder.reservation.dto.DashboardResponse.RoomUsage;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.service.RoomService;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 15);
    private static final LocalDate MONTH_START = LocalDate.of(2026, 3, 1);
    private static final LocalDate MONTH_END = LocalDate.of(2026, 3, 31);
    private static final List<ReservationStatus> APPROVED = List.of(ReservationStatus.CONFIRMADA,
            ReservationStatus.CANCELADA);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RoomService roomService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC);
        service = new DashboardService(reservationRepository, roomService, clock);
    }

    private static List<Object[]> rows(Object[]... rows) {
        return new ArrayList<>(List.of(rows));
    }

    private static Object[] usage(long roomId, long reservations, long minutes) {
        return new Object[] { roomId, reservations, minutes };
    }

    private void usageRows(LocalDate from, LocalDate to, List<Object[]> rows) {
        when(reservationRepository.summarizeUsageByRoom(ReservationStatus.CONFIRMADA, from, to)).thenReturn(rows);
    }

    private static Map<Long, String> names(Object... pairs) {
        Map<Long, String> names = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            names.put(((Number) pairs[i]).longValue(), (String) pairs[i + 1]);
        }
        return names;
    }

    @Test
    void shouldUseCurrentMonthWhenPeriodIsNotInformed() {
        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.from()).isEqualTo(MONTH_START);
        assertThat(dashboard.to()).isEqualTo(MONTH_END);
        assertThat(dashboard.periodError()).isNull();
    }

    @Test
    void shouldUseRequestedPeriod() {
        LocalDate from = LocalDate.of(2026, 1, 10);
        LocalDate to = LocalDate.of(2026, 2, 5);
        when(reservationRepository.countByStatusAndDateBetween(ReservationStatus.CONFIRMADA, from, to)).thenReturn(7L);

        DashboardResponse dashboard = service.buildDashboard(from, to);

        assertThat(dashboard.from()).isEqualTo(from);
        assertThat(dashboard.to()).isEqualTo(to);
        assertThat(dashboard.confirmedCount()).isEqualTo(7L);
    }

    @Test
    void shouldFallBackToCurrentMonthWhenEndIsBeforeStart() {
        DashboardResponse dashboard = service.buildDashboard(LocalDate.of(2026, 3, 10), LocalDate.of(2026, 3, 9));

        assertThat(dashboard.periodError()).isEqualTo("A data final não pode ser anterior à data inicial.");
        assertThat(dashboard.from()).isEqualTo(MONTH_START);
        assertThat(dashboard.to()).isEqualTo(MONTH_END);
    }

    @Test
    void shouldAcceptSingleDayPeriod() {
        DashboardResponse dashboard = service.buildDashboard(TODAY, TODAY);

        assertThat(dashboard.periodError()).isNull();
        assertThat(dashboard.from()).isEqualTo(TODAY);
    }

    @Test
    void shouldAcceptPeriodOfExactlyMaxDays() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = from.plusDays(DashboardService.MAX_PERIOD_DAYS - 1);

        DashboardResponse dashboard = service.buildDashboard(from, to);

        assertThat(dashboard.periodError()).isNull();
        assertThat(dashboard.to()).isEqualTo(to);
    }

    @Test
    void shouldRejectPeriodLongerThanMaxDays() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        DashboardResponse dashboard = service.buildDashboard(from, from.plusDays(DashboardService.MAX_PERIOD_DAYS));

        assertThat(dashboard.periodError()).isEqualTo("O período deve ter no máximo 366 dias.");
        assertThat(dashboard.from()).isEqualTo(MONTH_START);
    }

    @Test
    void shouldCountPendingNowRegardlessOfPeriod() {
        when(reservationRepository.countByStatus(ReservationStatus.PENDENTE)).thenReturn(4L);

        assertThat(service.buildDashboard(null, null).pendingNowCount()).isEqualTo(4L);
    }

    @Test
    void shouldCountFinishedUntilCurrentMoment() {
        when(reservationRepository.countFinished(ReservationStatus.CONFIRMADA, MONTH_START, MONTH_END, TODAY,
                LocalTime.of(10, 0))).thenReturn(3L);

        assertThat(service.buildDashboard(null, null).finishedCount()).isEqualTo(3L);
    }

    @Test
    void shouldComputeNoCheckInRateUsingFifteenMinuteWindow() {
        when(reservationRepository.countByStatusAndCancellationReasonAndDateBetween(ReservationStatus.CANCELADA,
                CancellationReason.SEM_CHECKIN, MONTH_START, MONTH_END)).thenReturn(1L);
        when(reservationRepository.countStartedUntil(ReservationStatus.CONFIRMADA, MONTH_START, MONTH_END, TODAY,
                LocalTime.of(9, 45))).thenReturn(3L);

        assertThat(service.buildDashboard(null, null).noCheckInRate()).isEqualTo("25,0%");
    }

    @Test
    void shouldShowNoDataWhenThereIsNothingToMeasure() {
        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.noCheckInRate()).isEqualTo(DashboardFormat.NO_DATA);
        assertThat(dashboard.cancellationRate()).isEqualTo(DashboardFormat.NO_DATA);
        assertThat(dashboard.requests().approvedPercent()).isEqualTo(DashboardFormat.NO_DATA);
    }

    @Test
    void shouldComputeCancellationRateOverConfirmedAndCancelled() {
        when(reservationRepository.countByStatusAndDateBetween(ReservationStatus.CONFIRMADA, MONTH_START, MONTH_END))
                .thenReturn(2L);
        when(reservationRepository.countByStatusAndDateBetween(ReservationStatus.CANCELADA, MONTH_START, MONTH_END))
                .thenReturn(1L);

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.cancellationRate()).isEqualTo("33,3%");
        assertThat(dashboard.cancelledTotal()).isEqualTo(1L);
    }

    @Test
    void shouldSplitCancellationsByReason() {
        when(reservationRepository.countCancelledByReason(ReservationStatus.CANCELADA, MONTH_START, MONTH_END))
                .thenReturn(rows(new Object[] { CancellationReason.SEM_CHECKIN, 3L },
                        new Object[] { CancellationReason.PELO_RESPONSAVEL, 1L }));

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.cancellationsByReason())
                .extracting(share -> share.label() + "|" + share.count() + "|" + share.percent())
                .containsExactly("Cancelada por falta de check-in|3|75,0%", "Cancelada pelo responsável|1|25,0%");
    }

    @Test
    void shouldMarkOnlyTheFirstThreeRoomsAsTop() {
        usageRows(MONTH_START, MONTH_END,
                rows(usage(1, 5, 300), usage(2, 4, 90), usage(3, 2, 60), usage(4, 1, 30)));
        when(roomService.findRoomNames(List.of(1L, 2L, 3L, 4L))).thenReturn(names(1, "A", 2, "B", 3, "C", 4, "D"));

        List<RoomUsage> usage = service.buildDashboard(null, null).roomUsage();

        assertThat(usage).extracting(RoomUsage::top).containsExactly(true, true, true, false);
        assertThat(usage.get(1).roomName()).isEqualTo("B");
        assertThat(usage.get(1).duration()).isEqualTo("1h 30min");
        assertThat(usage.get(0).reservationCount()).isEqualTo(5L);
    }

    @Test
    void shouldSummarizeRequestsByOutcome() {
        when(reservationRepository.countApprovedRequests(APPROVED, MONTH_START, MONTH_END)).thenReturn(2L);
        when(reservationRepository.countEndedRequests(ReservationStatus.RECUSADA, MONTH_START, MONTH_END))
                .thenReturn(1L);
        when(reservationRepository.countEndedRequests(ReservationStatus.EXPIRADA, MONTH_START, MONTH_END))
                .thenReturn(1L);
        when(reservationRepository.countSelfApprovedRequests(APPROVED, MONTH_START, MONTH_END)).thenReturn(1L);

        DashboardResponse.RequestSummary requests = service.buildDashboard(null, null).requests();

        assertThat(requests.total()).isEqualTo(4L);
        assertThat(requests.approvedPercent()).isEqualTo("50,0%");
        assertThat(requests.rejectedPercent()).isEqualTo("25,0%");
        assertThat(requests.expiredPercent()).isEqualTo("25,0%");
        assertThat(requests.selfApprovedCount()).isEqualTo(1L);
    }

    @Test
    void shouldShowNoDataWhenThereIsNoApprovalTime() {
        when(reservationRepository.averageApprovalSeconds(APPROVED, MONTH_START, MONTH_END)).thenReturn(null);

        assertThat(service.buildDashboard(null, null).averageApprovalTime()).isEqualTo(DashboardFormat.NO_DATA);
    }

    @Test
    void shouldRoundAverageApprovalTimeToMinutes() {
        when(reservationRepository.averageApprovalSeconds(APPROVED, MONTH_START, MONTH_END)).thenReturn(5_430.0);

        assertThat(service.buildDashboard(null, null).averageApprovalTime()).isEqualTo("1h 31min");
    }

    @Test
    void comparisonShouldBeEmptyWhenNoRoomWasUsed() {
        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.empty()).isTrue();
        assertThat(comparison.summary()).isEqualTo("Nenhuma sala para exibir");
    }

    @Test
    void comparisonShouldShowMostUsedRoomsByDefault() {
        usageRows(MONTH_START, MONTH_END, rows(usage(1, 2, 120), usage(2, 1, 60)));
        when(roomService.findRoomNames(List.of(1L, 2L))).thenReturn(names(1, "A", 2, "B"));
        when(roomService.findActiveRoomNames()).thenReturn(names(1, "A", 2, "B", 3, "C"));

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.bars()).extracting(RoomBar::label).containsExactly("A", "B");
        assertThat(comparison.summary()).isEqualTo("Exibindo as 2 salas com mais horas");
        assertThat(comparison.options()).extracting(RoomOption::name).containsExactly("A", "B", "C");
        assertThat(comparison.selectedIds()).isEmpty();
    }

    @Test
    void comparisonShouldLimitDefaultViewToFiveRooms() {
        usageRows(MONTH_START, MONTH_END, rows(usage(1, 1, 600), usage(2, 1, 500), usage(3, 1, 400),
                usage(4, 1, 300), usage(5, 1, 200), usage(6, 1, 100)));
        when(roomService.findRoomNames(any())).thenReturn(names(1, "A", 2, "B", 3, "C", 4, "D", 5, "E", 6, "F"));

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.bars()).hasSize(5).extracting(RoomBar::roomId).doesNotContain(6L);
    }

    @Test
    void comparisonShouldUseSingularSummaryForOneRoom() {
        usageRows(MONTH_START, MONTH_END, rows(usage(1, 1, 30)));
        when(roomService.findRoomNames(List.of(1L))).thenReturn(names(1, "A"));

        assertThat(service.buildDashboard(null, null).roomComparison().summary())
                .isEqualTo("Exibindo a sala com mais horas");
    }

    @Test
    void comparisonShouldIncludeInactiveRoomThatWasUsedAmongOptions() {
        usageRows(MONTH_START, MONTH_END, rows(usage(9, 1, 60)));
        when(roomService.findRoomNames(List.of(9L))).thenReturn(names(9, "Sala antiga"));
        when(roomService.findActiveRoomNames()).thenReturn(names(1, "A"));

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.options()).extracting(RoomOption::roomId).containsExactly(1L, 9L);
    }

    @Test
    void comparisonShouldShowSelectedRoomsIncludingOnesWithoutUsage() {
        when(roomService.findActiveRoomNames()).thenReturn(names(1, "A", 2, "B", 3, "C"));
        when(reservationRepository.summarizeUsageForRooms(ReservationStatus.CONFIRMADA, MONTH_START, MONTH_END,
                List.of(2L, 3L))).thenReturn(rows(new Object[] { 3L, 90L }));

        DashboardResponse.RoomComparison comparison = service
                .buildDashboard(null, null, List.of(2L, 3L, 2L)).roomComparison();

        assertThat(comparison.bars()).extracting(bar -> bar.label() + "=" + bar.minutes())
                .containsExactly("C=90", "B=0");
        assertThat(comparison.selectedIds()).containsExactly(2L, 3L);
        assertThat(comparison.summary()).isEqualTo("Exibindo 2 salas selecionadas");
        assertThat(comparison.expanded()).isTrue();
    }

    @Test
    void comparisonShouldIgnoreUnknownSelectedRooms() {
        when(roomService.findActiveRoomNames()).thenReturn(names(1, "A"));
        when(reservationRepository.summarizeUsageForRooms(ReservationStatus.CONFIRMADA, MONTH_START, MONTH_END,
                List.of(1L))).thenReturn(rows());

        DashboardResponse.RoomComparison comparison = service
                .buildDashboard(null, null, List.of(1L, 404L)).roomComparison();

        assertThat(comparison.selectedIds()).containsExactly(1L);
        assertThat(comparison.summary()).isEqualTo("Exibindo 1 sala selecionada");
    }

    @Test
    void comparisonShouldRejectMoreThanFiveSelectedRooms() {
        usageRows(MONTH_START, MONTH_END, rows(usage(1, 1, 60)));
        when(roomService.findRoomNames(List.of(1L))).thenReturn(names(1, "A"));

        DashboardResponse.RoomComparison comparison = service
                .buildDashboard(null, null, List.of(1L, 2L, 3L, 4L, 5L, 6L)).roomComparison();

        assertThat(comparison.error()).isEqualTo("Selecione no máximo 5 salas");
        assertThat(comparison.selectedIds()).isEmpty();
        assertThat(comparison.bars()).extracting(RoomBar::roomId).containsExactly(1L);
        verify(reservationRepository, never()).summarizeUsageForRooms(any(), any(), any(), anyCollection());
    }

    @Test
    void comparisonWithAllRoomsShouldIncludeRoomsWithoutUsage() {
        usageRows(MONTH_START, MONTH_END, rows(usage(2, 1, 60)));
        when(roomService.findRoomNames(List.of(2L))).thenReturn(names(2, "B"));
        when(roomService.findActiveRoomNames()).thenReturn(names(1, "A", 2, "B"));

        DashboardResponse.RoomComparison comparison = service
                .buildDashboard(null, null, List.of(1L), true).roomComparison();

        assertThat(comparison.bars()).extracting(bar -> bar.label() + "=" + bar.minutes())
                .containsExactly("B=60", "A=0");
        assertThat(comparison.all()).isTrue();
        assertThat(comparison.selectedIds()).isEmpty();
        assertThat(comparison.summary()).isEqualTo("Exibindo todas as 2 salas");
    }

    @Test
    void comparisonWithAllRoomsShouldLimitChartToTenRooms() {
        Map<Long, String> active = new LinkedHashMap<>();
        for (long id = 1; id <= 12; id++) {
            active.put(id, "Sala " + (char) ('A' + id - 1));
        }
        when(roomService.findActiveRoomNames()).thenReturn(active);

        DashboardResponse.RoomComparison comparison = service
                .buildDashboard(null, null, null, true).roomComparison();

        assertThat(comparison.bars()).hasSize(DashboardService.MAX_CHART_ROOMS);
        assertThat(comparison.bars().get(0).label()).isEqualTo("Sala A");
        assertThat(comparison.options()).hasSize(12);
        assertThat(comparison.summary()).isEqualTo("Exibindo as 10 salas com mais horas (todas)");
    }

    @Test
    void comparisonWithAllRoomsShouldUseSingularSummaryForOneRoom() {
        when(roomService.findActiveRoomNames()).thenReturn(names(1, "A"));

        assertThat(service.buildDashboard(null, null, null, true).roomComparison().summary())
                .isEqualTo("Exibindo a única sala");
    }

    @Test
    void comparisonShouldBreakTiesByLabel() {
        usageRows(MONTH_START, MONTH_END, rows(usage(1, 1, 60), usage(2, 1, 60)));
        when(roomService.findRoomNames(List.of(1L, 2L))).thenReturn(names(1, "Zeta", 2, "Alfa"));

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.bars()).extracting(RoomBar::label).containsExactly("Alfa", "Zeta");
    }

    @Test
    void shouldQueryUsageForTheEffectivePeriodAfterFallback() {
        service.buildDashboard(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));

        verify(reservationRepository).summarizeUsageByRoom(eq(ReservationStatus.CONFIRMADA), eq(MONTH_START),
                eq(MONTH_END));
    }
}
