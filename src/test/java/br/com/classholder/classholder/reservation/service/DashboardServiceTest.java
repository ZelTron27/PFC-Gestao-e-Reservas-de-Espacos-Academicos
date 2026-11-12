package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.dto.DashboardResponse;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.room.service.RoomService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DashboardServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final LocalDate IN_PERIOD = LocalDate.of(2026, 6, 10);
    private static final LocalDate OUT_OF_PERIOD = LocalDate.of(2026, 7, 10);
    private static final Long RESPONSIBLE = 1L;
    private static final Long EVALUATOR = 99L;
    private static final Long DELETED_ROOM_ID = 987654L;

    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private RoomService roomService;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(ZonedDateTime.of(TODAY, LocalTime.of(10, 0), ZONE).toInstant(), ZONE);
        service = new DashboardService(reservationRepository, roomService, clock);
    }

    @Test
    void confirmedCount_shouldCountOnlyConfirmedInPeriodAndReportFinishedOnes() {
        Long room = createRoom("Sala A").getId();
        reservation(room, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(room, TODAY, "08:00", "09:30", ReservationStatus.CONFIRMADA);
        reservation(room, TODAY, "11:00", "12:00", ReservationStatus.CONFIRMADA);
        reservation(room, OUT_OF_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(room, IN_PERIOD, "13:00", "14:00", ReservationStatus.CANCELADA);

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.confirmedCount()).isEqualTo(3);
        assertThat(dashboard.finishedCount()).isEqualTo(2);
    }

    @Test
    void pendingNowCount_shouldIgnoreThePeriod() {
        Long room = createRoom("Sala A").getId();
        reservation(room, IN_PERIOD, "08:00", "09:00", ReservationStatus.PENDENTE);
        reservation(room, OUT_OF_PERIOD, "08:00", "09:00", ReservationStatus.PENDENTE);
        reservation(room, IN_PERIOD, "10:00", "11:00", ReservationStatus.CONFIRMADA);

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.pendingNowCount()).isEqualTo(2);
    }

    @Test
    void noCheckInRate_shouldCountOnlyReservationsWhoseCheckInWindowIsClosed() {
        Long room = createRoom("Sala A").getId();
        reservation(room, TODAY, "09:50", "10:50", ReservationStatus.CONFIRMADA);
        reservation(room, TODAY, "09:40", "10:40", ReservationStatus.CONFIRMADA);
        cancelled(room, IN_PERIOD, "08:00", "09:00", CancellationReason.SEM_CHECKIN);
        cancelled(room, OUT_OF_PERIOD, "08:00", "09:00", CancellationReason.SEM_CHECKIN);
        cancelled(room, IN_PERIOD, "11:00", "12:00", CancellationReason.PELO_RESPONSAVEL);

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.noCheckInRate()).isEqualTo("50,0%");
    }

    @Test
    void noCheckInRate_withReservationStartedTenMinutesAgoOnly_shouldReportNoData() {
        Long room = createRoom("Sala A").getId();
        reservation(room, TODAY, "09:50", "10:50", ReservationStatus.CONFIRMADA);

        assertThat(service.buildDashboard(null, null).noCheckInRate()).isEqualTo("Sem dados");
    }

    @Test
    void cancellationRate_shouldDivideCancelledByCancelledPlusConfirmed() {
        Long room = createRoom("Sala A").getId();
        reservation(room, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(room, IN_PERIOD, "09:00", "10:00", ReservationStatus.CONFIRMADA);
        reservation(room, IN_PERIOD, "10:00", "11:00", ReservationStatus.CONFIRMADA);
        cancelled(room, IN_PERIOD, "11:00", "12:00", CancellationReason.PELA_GESTAO);
        cancelled(room, OUT_OF_PERIOD, "11:00", "12:00", CancellationReason.PELA_GESTAO);

        assertThat(service.buildDashboard(null, null).cancellationRate()).isEqualTo("25,0%");
    }

    @Test
    void rates_withEmptyDenominators_shouldReportNoData() {
        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.noCheckInRate()).isEqualTo("Sem dados");
        assertThat(dashboard.cancellationRate()).isEqualTo("Sem dados");
        assertThat(dashboard.averageApprovalTime()).isEqualTo("Sem dados");
    }

    @Test
    void cancellationsByReason_shouldGroupWithLabelCountAndPercent() {
        Long room = createRoom("Sala A").getId();
        cancelled(room, IN_PERIOD, "08:00", "09:00", CancellationReason.PELA_GESTAO);
        cancelled(room, IN_PERIOD, "09:00", "10:00", CancellationReason.PELA_GESTAO);
        cancelled(room, IN_PERIOD, "10:00", "11:00", CancellationReason.PELA_GESTAO);
        cancelled(room, IN_PERIOD, "11:00", "12:00", CancellationReason.SEM_CHECKIN);
        cancelled(room, OUT_OF_PERIOD, "11:00", "12:00", CancellationReason.SEM_CHECKIN);

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.cancellationsByReason()).extracting("label", "count", "percent")
                .containsExactly(
                        tuple(CancellationReason.PELA_GESTAO.label(), 3L, "75,0%"),
                        tuple(CancellationReason.SEM_CHECKIN.label(), 1L, "25,0%"));
    }

    @Test
    void roomUsage_shouldSumDurationsOrderByHoursAndNameDeletedRooms() {
        Long roomA = createRoom("Sala A").getId();
        Long roomB = createRoom("Sala B").getId();
        Long roomC = createRoom("Sala C").getId();
        Long roomD = createRoom("Sala D").getId();
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(roomA, IN_PERIOD, "09:00", "09:30", ReservationStatus.CONFIRMADA);
        reservation(roomB, IN_PERIOD, "08:00", "11:00", ReservationStatus.CONFIRMADA);
        reservation(DELETED_ROOM_ID, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(roomC, IN_PERIOD, "08:00", "08:30", ReservationStatus.CONFIRMADA);
        reservation(roomD, IN_PERIOD, "08:00", "12:00", ReservationStatus.CANCELADA);
        reservation(roomD, OUT_OF_PERIOD, "08:00", "12:00", ReservationStatus.CONFIRMADA);

        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.roomUsage()).extracting("roomName", "reservationCount", "duration", "top")
                .containsExactly(
                        tuple("Sala B", 1L, "3h 0min", true),
                        tuple("Sala A", 2L, "1h 30min", true),
                        tuple("Sala excluída (ID " + DELETED_ROOM_ID + ")", 1L,
                                "1h 0min", true),
                        tuple("Sala C", 1L, "0h 30min", false));
    }

    @Test
    void requests_shouldExcludeSelfApprovalsFromPercentagesAndCountThemSeparately() {
        Long room = createRoom("Sala Especial").getId();
        approved(room, IN_PERIOD, "08:00", "09:00", EVALUATOR, 60);
        approved(room, IN_PERIOD, "09:00", "10:00", EVALUATOR, 120);
        approved(room, IN_PERIOD, "10:00", "11:00", RESPONSIBLE, 500);
        approved(room, OUT_OF_PERIOD, "10:00", "11:00", EVALUATOR, 10);
        reservation(room, IN_PERIOD, "11:00", "12:00", ReservationStatus.RECUSADA, EVALUATOR);
        reservation(room, IN_PERIOD, "12:00", "13:00", ReservationStatus.EXPIRADA, null);
        reservation(room, IN_PERIOD, "13:00", "14:00", ReservationStatus.EXPIRADA, null);

        DashboardResponse.RequestSummary requests = service.buildDashboard(null, null).requests();

        assertThat(requests.total()).isEqualTo(5);
        assertThat(requests.approvedCount()).isEqualTo(2);
        assertThat(requests.approvedPercent()).isEqualTo("40,0%");
        assertThat(requests.rejectedCount()).isEqualTo(1);
        assertThat(requests.rejectedPercent()).isEqualTo("20,0%");
        assertThat(requests.expiredCount()).isEqualTo(2);
        assertThat(requests.expiredPercent()).isEqualTo("40,0%");
        assertThat(requests.selfApprovedCount()).isEqualTo(1);
    }

    @Test
    void requests_withoutData_shouldReportNoDataPercentages() {
        DashboardResponse.RequestSummary requests = service.buildDashboard(null, null).requests();

        assertThat(requests.total()).isZero();
        assertThat(requests.approvedPercent()).isEqualTo("Sem dados");
    }

    @Test
    void averageApprovalTime_shouldIgnoreSelfApprovalsAndOutOfPeriod() {
        Long room = createRoom("Sala Especial").getId();
        approved(room, IN_PERIOD, "08:00", "09:00", EVALUATOR, 60);
        approved(room, IN_PERIOD, "09:00", "10:00", EVALUATOR, 120);
        approved(room, IN_PERIOD, "10:00", "11:00", RESPONSIBLE, 5000);
        approved(room, OUT_OF_PERIOD, "10:00", "11:00", EVALUATOR, 1000);

        assertThat(service.buildDashboard(null, null).averageApprovalTime()).isEqualTo("1h 30min");
    }

    @Test
    void period_withoutParameters_shouldDefaultToCurrentMonth() {
        DashboardResponse dashboard = service.buildDashboard(null, null);

        assertThat(dashboard.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(dashboard.to()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(dashboard.periodError()).isNull();
    }

    @Test
    void period_withValidRange_shouldUseRequestedDates() {
        DashboardResponse dashboard = service.buildDashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(dashboard.from()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(dashboard.to()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(dashboard.periodError()).isNull();
    }

    @Test
    void period_withEndBeforeStart_shouldReturnMessageAndDefaultPeriod() {
        DashboardResponse dashboard = service.buildDashboard(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 9));

        assertThat(dashboard.periodError()).isEqualTo("A data final não pode ser anterior à data inicial.");
        assertThat(dashboard.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(dashboard.to()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void period_longerThan366Days_shouldReturnMessageAndDefaultPeriod() {
        DashboardResponse dashboard = service.buildDashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2));

        assertThat(dashboard.periodError()).isEqualTo("O período deve ter no máximo 366 dias.");
        assertThat(dashboard.from()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(dashboard.to()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void period_ofExactly366Days_shouldBeAccepted() {
        DashboardResponse dashboard = service.buildDashboard(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1));

        assertThat(dashboard.periodError()).isNull();
        assertThat(dashboard.to()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void roomComparison_withoutSelection_shouldShowTopFiveRoomsByHours() {
        for (int hours = 1; hours <= 6; hours++) {
            Long room = createRoom("Sala " + hours).getId();
            reservation(room, IN_PERIOD, "06:00", LocalTime.of(6 + hours, 0).toString(),
                    ReservationStatus.CONFIRMADA);
        }

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.bars()).extracting("label")
                .containsExactly("Sala 6", "Sala 5", "Sala 4", "Sala 3", "Sala 2");
        assertThat(comparison.error()).isNull();
    }

    @Test
    void roomComparison_withOnlyThreeRoomsReserved_shouldShowThoseThree() {
        Long roomA = createRoom("Sala A").getId();
        Long roomB = createRoom("Sala B").getId();
        Long roomC = createRoom("Sala C").getId();
        createRoom("Sala D");
        createRoom("Sala E");
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(roomB, IN_PERIOD, "08:00", "10:00", ReservationStatus.CONFIRMADA);
        reservation(roomC, IN_PERIOD, "08:00", "11:00", ReservationStatus.CONFIRMADA);

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.bars()).extracting("label").containsExactly("Sala C", "Sala B", "Sala A");
    }

    @Test
    void roomComparison_withoutReservations_shouldBeEmptyAndKeepActiveRoomOptions() {
        createRoom("Sala A");
        createRoom("Sala B");

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null).roomComparison();

        assertThat(comparison.empty()).isTrue();
        assertThat(comparison.options()).extracting("name").contains("Sala A", "Sala B");
    }

    @Test
    void roomComparison_withTwoSelectedRooms_shouldShowOnlyThemOrderedByHours() {
        Long roomA = createRoom("Sala A").getId();
        Long roomB = createRoom("Sala B").getId();
        Long roomC = createRoom("Sala C").getId();
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(roomB, IN_PERIOD, "08:00", "11:00", ReservationStatus.CONFIRMADA);
        reservation(roomC, IN_PERIOD, "08:00", "12:00", ReservationStatus.CONFIRMADA);

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, List.of(roomA, roomB))
                .roomComparison();

        assertThat(comparison.bars()).extracting("label", "duration")
                .containsExactly(tuple("Sala B", "3h 0min"), tuple("Sala A", "1h 0min"));
        assertThat(comparison.options()).filteredOn(option -> option.selected()).extracting("roomId")
                .containsExactlyInAnyOrder(roomA, roomB);
    }

    @Test
    void roomComparison_withSelectedRoomWithoutReservations_shouldShowZeroHours() {
        Long roomA = createRoom("Sala A").getId();
        Long roomB = createRoom("Sala B").getId();
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, List.of(roomA, roomB))
                .roomComparison();

        assertThat(comparison.bars()).extracting("label", "minutes")
                .containsExactly(tuple("Sala A", 60L), tuple("Sala B", 0L));
    }

    @Test
    void roomComparison_withAllRooms_shouldShowEveryRoomWithoutLimitIncludingZeroHoursAndDeletedRooms() {
        for (int index = 1; index <= 6; index++) {
            Long room = createRoom("Sala " + index).getId();
            reservation(room, IN_PERIOD, "08:00", LocalTime.of(8 + index, 0).toString(),
                    ReservationStatus.CONFIRMADA);
        }
        createRoom("Sala Vazia");
        reservation(DELETED_ROOM_ID, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        String deletedName = "Sala excluída (ID " + DELETED_ROOM_ID + ")";

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, null, true)
                .roomComparison();

        assertThat(comparison.all()).isTrue();
        assertThat(comparison.error()).isNull();
        assertThat(comparison.bars()).extracting("label").containsExactly("Sala 6", "Sala 5", "Sala 4", "Sala 3",
                "Sala 2", "Sala 1", deletedName, "Sala Vazia");
        assertThat(comparison.bars()).filteredOn(bar -> bar.label().equals("Sala Vazia")).extracting("minutes")
                .containsExactly(0L);
    }

    @Test
    void roomComparison_withAllRoomsAndMoreThanTenRooms_shouldShowTheTenWithMostHoursInDescendingOrder() {
        for (int index = 1; index <= 12; index++) {
            Long room = createRoom("Sala " + String.format("%02d", index)).getId();
            reservation(room, IN_PERIOD, "06:00", LocalTime.of(6, 0).plusMinutes(index * 30L).toString(),
                    ReservationStatus.CONFIRMADA);
        }

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, null, true)
                .roomComparison();

        assertThat(comparison.bars()).hasSize(DashboardService.MAX_CHART_ROOMS);
        assertThat(comparison.bars()).extracting("label").containsExactly("Sala 12", "Sala 11", "Sala 10",
                "Sala 09", "Sala 08", "Sala 07", "Sala 06", "Sala 05", "Sala 04", "Sala 03");
        assertThat(comparison.summary()).isEqualTo("Exibindo as 10 salas com mais horas (todas)");
        assertThat(service.buildDashboard(null, null).roomUsage()).hasSize(12);
    }

    @Test
    void roomComparison_withAllRoomsAndFewerThanTenRooms_shouldShowAllOfThem() {
        for (int index = 1; index <= 4; index++) {
            createRoom("Sala " + index);
        }

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, null, true)
                .roomComparison();

        assertThat(comparison.bars()).hasSize(4);
        assertThat(comparison.summary()).isEqualTo("Exibindo todas as 4 salas");
    }

    @Test
    void roomComparison_summary_shouldDescribeDefaultAndSelection() {
        Long roomA = createRoom("Sala A").getId();
        Long roomB = createRoom("Sala B").getId();
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(roomB, IN_PERIOD, "08:00", "10:00", ReservationStatus.CONFIRMADA);

        assertThat(service.buildDashboard(null, null).roomComparison().summary())
                .isEqualTo("Exibindo as 2 salas com mais horas");
        assertThat(service.buildDashboard(null, null, List.of(roomA, roomB)).roomComparison().summary())
                .isEqualTo("Exibindo 2 salas selecionadas");
        assertThat(service.buildDashboard(null, null, List.of(roomA)).roomComparison().summary())
                .isEqualTo("Exibindo 1 sala selecionada");
    }

    @Test
    void roomComparison_withAllRoomsAndSelectedIds_shouldIgnoreTheSelectionAndTheFiveRoomLimit() {
        List<Long> ids = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            ids.add(createRoom("Sala " + index).getId());
        }

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, ids, true).roomComparison();

        assertThat(comparison.error()).isNull();
        assertThat(comparison.bars()).hasSize(6);
    }

    @Test
    void roomComparison_withoutAllRooms_shouldReportAllAsFalse() {
        assertThat(service.buildDashboard(null, null).roomComparison().all()).isFalse();
    }

    @Test
    void roomComparison_withSixRooms_shouldReportErrorAndFallBackToDefault() {
        List<Long> ids = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            Long room = createRoom("Sala " + index).getId();
            ids.add(room);
            reservation(room, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        }

        DashboardResponse.RoomComparison comparison = service.buildDashboard(null, null, ids).roomComparison();

        assertThat(comparison.error()).isEqualTo("Selecione no máximo 5 salas");
        assertThat(comparison.bars()).hasSize(5);
        assertThat(comparison.options()).noneMatch(option -> option.selected());
    }

    @Test
    void roomComparison_withInvalidIds_shouldIgnoreThemAndFallBackToDefaultWhenNoneIsValid() {
        Long roomA = createRoom("Sala A").getId();
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);

        DashboardResponse.RoomComparison onlyInvalid = service.buildDashboard(null, null, List.of(555111L))
                .roomComparison();
        DashboardResponse.RoomComparison mixed = service.buildDashboard(null, null, List.of(555111L, roomA))
                .roomComparison();

        assertThat(onlyInvalid.bars()).extracting("label").containsExactly("Sala A");
        assertThat(onlyInvalid.error()).isNull();
        assertThat(mixed.bars()).extracting("label").containsExactly("Sala A");
    }

    @Test
    void roomComparison_shouldNameDeletedRoomsWithReservationsAndAllowSelectingThem() {
        Long roomA = createRoom("Sala A").getId();
        reservation(roomA, IN_PERIOD, "08:00", "09:00", ReservationStatus.CONFIRMADA);
        reservation(DELETED_ROOM_ID, IN_PERIOD, "08:00", "11:00", ReservationStatus.CONFIRMADA);
        String deletedName = "Sala excluída (ID " + DELETED_ROOM_ID + ")";

        DashboardResponse.RoomComparison defaults = service.buildDashboard(null, null).roomComparison();
        DashboardResponse.RoomComparison selected = service.buildDashboard(null, null, List.of(DELETED_ROOM_ID))
                .roomComparison();

        assertThat(defaults.bars()).extracting("label").containsExactly(deletedName, "Sala A");
        assertThat(defaults.options()).extracting("name").contains(deletedName);
        assertThat(selected.bars()).extracting("label").containsExactly(deletedName);
    }

    private Room createRoom(String name) {
        return roomRepository.save(Room.builder()
                .roomName(name)
                .roomType("Laboratório")
                .localization("Prédio 1")
                .roomCapacity(20)
                .description("Sala de teste")
                .active(true)
                .build());
    }

    private Reservation reservation(Long roomId, LocalDate date, String start, String end,
            ReservationStatus status) {
        return reservation(roomId, date, start, end, status, null);
    }

    private Reservation reservation(Long roomId, LocalDate date, String start, String end,
            ReservationStatus status, Long evaluatedById) {
        return reservationRepository.save(Reservation.builder()
                .roomId(roomId)
                .responsibleId(RESPONSIBLE)
                .date(date)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .purpose("Teste")
                .status(status)
                .evaluatedById(evaluatedById)
                .build());
    }

    private void cancelled(Long roomId, LocalDate date, String start, String end, CancellationReason reason) {
        Reservation reservation = reservation(roomId, date, start, end, ReservationStatus.CANCELADA);
        reservation.setCancellationReason(reason);
        reservationRepository.save(reservation);
    }

    private void approved(Long roomId, LocalDate date, String start, String end, Long evaluatorId,
            long minutesToApprove) {
        Reservation reservation = reservation(roomId, date, start, end, ReservationStatus.CONFIRMADA, evaluatorId);
        Instant createdAt = reservation.getCreatedAt();
        reservation.setEvaluatedAt(createdAt.plusSeconds(minutesToApprove * 60));
        reservationRepository.save(reservation);
    }

}
