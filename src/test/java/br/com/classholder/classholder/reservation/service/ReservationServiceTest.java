package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.classholder.classholder.holiday.service.HolidayService;
import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.dto.ReservationRequest;
import br.com.classholder.classholder.reservation.dto.ReservationResponse;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long ROOM_ID = 1L;
    private static final Long PROFESSOR_ID = 7L;
    private static final Long COORDINATOR_ID = 8L;
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 9, 30);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 10, 3);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 10, 4);
    private static final LocalDate CORPUS_CHRISTI = LocalDate.of(2026, 6, 4);

    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private RoomService roomService;
    @Mock
    private UserService userService;
    @Mock
    private HolidayService holidayService;

    private ReservationService service;
    private LocalDate nextMonday;

    @BeforeEach
    void setUp() {
        service = new ReservationService(reservationRepository, roomService, userService, holidayService, true);
        nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        lenient().when(roomService.findRoomForReservation(anyLong()))
                .thenAnswer(invocation -> roomService.findRoomById(invocation.getArgument(0)));
        lenient().when(reservationRepository.findRoomIdById(anyLong())).thenReturn(Optional.of(ROOM_ID));
    }

    @Test
    void shouldCreateReservationWhenRoomIsFreeAndWithinOperatingHours() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());
        when(reservationRepository.findConflicting(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(userService.getUserById(PROFESSOR_ID))
                .thenReturn(new UserResponse(PROFESSOR_ID, "Maria", "maria@teste.com", UserRole.PROFESSOR));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });

        ReservationResponse response = service.reserveCommonRoom(PROFESSOR_ID, request);

        assertEquals(99L, response.id());
        assertEquals("Sala 12", response.roomName());
        assertEquals("Maria", response.responsibleName());
        assertEquals(ReservationStatus.CONFIRMADA, response.status());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void shouldThrowWhenConfirmedReservationAlreadyExistsAtThatTime() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, request.startTime(), request.endTime(),
                ReservationStatus.CONFIRMADA)).thenReturn(List.of(new Reservation()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertEquals("Já existe uma reserva para essa sala nesse dia e horário", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"10:00,10:00", "11:00,10:00"})
    void shouldThrowWhenStartIsNotBeforeEnd(String start, String end) {
        ReservationRequest request = request(LocalTime.parse(start), LocalTime.parse(end));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertTrue(ex.getMessage().contains("início deve ser anterior"));
        verify(reservationRepository, never()).save(any());
        verify(roomService, never()).findRoomById(anyLong());
    }

    @ParameterizedTest
    @CsvSource({"10:00,13:30", "10:00,14:00", "08:00,17:00"})
    void shouldThrowWhenDurationIsLongerThanThreeHours(String start, String end) {
        ReservationRequest request = request(LocalTime.parse(start), LocalTime.parse(end));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertEquals("A reserva deve ter no máximo 3 horas", ex.getMessage());
        verify(reservationRepository, never()).save(any());
        verify(roomService, never()).findRoomById(anyLong());
    }

    @Test
    void shouldThrowWhenDurationIsShorterThanThirtyMinutes() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(10, 15));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertEquals("A reserva deve ter no mínimo 30 minutos", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({"10:15,11:15", "10:00,11:20"})
    void shouldThrowWhenTimesAreNotInThirtyMinuteBlocks(String start, String end) {
        ReservationRequest request = request(LocalTime.parse(start), LocalTime.parse(end));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertEquals("Os horários são oferecidos em blocos de 30 minutos", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldAcceptReservationOfExactlyThreeHours() {
        ReservationRequest request = request(LocalTime.of(14, 0), LocalTime.of(17, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());
        when(reservationRepository.findConflicting(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(userService.getUserById(PROFESSOR_ID))
                .thenReturn(new UserResponse(PROFESSOR_ID, "Maria", "maria@teste.com", UserRole.PROFESSOR));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(100L);
            return saved;
        });

        ReservationResponse response = service.reserveCommonRoom(PROFESSOR_ID, request);

        assertEquals(100L, response.id());
        assertEquals(ReservationStatus.CONFIRMADA, response.status());
    }

    @Test
    void shouldNotOfferStartTimesWhoseDurationExceedsOperatingHours() {
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());
        when(reservationRepository.findAllByRoomIdAndDateAndStatus(ROOM_ID, nextMonday,
                ReservationStatus.CONFIRMADA)).thenReturn(List.of());

        List<LocalTime> starts = service.listAvailableStartTimes(ROOM_ID, nextMonday, 180);

        assertThat(starts).isNotEmpty();
        assertThat(starts).doesNotContain(LocalTime.of(15, 30), LocalTime.of(16, 0), LocalTime.of(17, 30));
        assertThat(starts.get(starts.size() - 1)).isEqualTo(LocalTime.of(15, 0));
    }

    @Test
    void shouldNotOfferStartTimesOverlappingAConfirmedReservationForTheChosenDuration() {
        Reservation confirmed = Reservation.builder().id(3L).roomId(ROOM_ID).responsibleId(PROFESSOR_ID)
                .date(nextMonday).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 0)).purpose("Aula")
                .build();
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());
        when(reservationRepository.findAllByRoomIdAndDateAndStatus(ROOM_ID, nextMonday,
                ReservationStatus.CONFIRMADA)).thenReturn(List.of(confirmed));

        List<LocalTime> starts = service.listAvailableStartTimes(ROOM_ID, nextMonday, 60);

        assertThat(starts).doesNotContain(LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(11, 0),
                LocalTime.of(11, 30));
        assertThat(starts).contains(LocalTime.of(9, 0), LocalTime.of(12, 0));
    }

    @Test
    void shouldCancelFutureReservationByManagementWithReason() {
        Reservation reservation = reservationAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());

        service.cancelReservationByManagement(5L);

        assertEquals(ReservationStatus.CANCELADA, reservation.getStatus());
        assertEquals(CancellationReason.PELA_GESTAO, reservation.getCancellationReason());
        verify(reservationRepository).save(reservation);
    }

    @Test
    void shouldNotCancelByManagementWhenReservationAlreadyStarted() {
        Reservation reservation = reservationAt(LocalDateTime.now().minusMinutes(5));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.cancelReservationByManagement(5L));

        assertEquals("Não é possível cancelar uma reserva que já começou", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldCancelOwnReservationWithReason() {
        Reservation reservation = reservationAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());

        service.cancelOwnReservation(PROFESSOR_ID, 5L);

        assertEquals(ReservationStatus.CANCELADA, reservation.getStatus());
        assertEquals(CancellationReason.PELO_RESPONSAVEL, reservation.getCancellationReason());
    }

    @Test
    void shouldNotCancelReservationOfAnotherUser() {
        Reservation reservation = reservationAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalArgumentException.class, () -> service.cancelOwnReservation(99L, 5L));

        assertEquals(ReservationStatus.CONFIRMADA, reservation.getStatus());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldCheckInWithinFirstFifteenMinutes() {
        Reservation reservation = reservationAt(LocalDateTime.now().minusMinutes(5));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());

        service.checkIn(PROFESSOR_ID, 5L);

        assertNotNull(reservation.getCheckInAt());
        verify(reservationRepository).save(reservation);
    }

    @Test
    void shouldNotCheckInAfterFifteenMinutes() {
        Reservation reservation = reservationAt(LocalDateTime.now().minusMinutes(20));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalStateException.class, () -> service.checkIn(PROFESSOR_ID, 5L));

        assertNull(reservation.getCheckInAt());
    }

    @Test
    void shouldNotCheckInBeforeStart() {
        Reservation reservation = reservationAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalStateException.class, () -> service.checkIn(PROFESSOR_ID, 5L));
    }

    @Test
    void shouldCancelReservationWithMissedCheckIn() {
        Reservation reservation = reservationAt(LocalDateTime.now().minusMinutes(20));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());

        service.cancelForMissedCheckIn(5L);

        assertEquals(ReservationStatus.CANCELADA, reservation.getStatus());
        assertEquals(CancellationReason.SEM_CHECKIN, reservation.getCancellationReason());
    }

    @Test
    void shouldNotCancelForMissedCheckInWhileWindowIsOpen() {
        Reservation reservation = reservationAt(LocalDateTime.now().minusMinutes(5));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));

        assertThrows(IllegalStateException.class, () -> service.cancelForMissedCheckIn(5L));

        assertEquals(ReservationStatus.CONFIRMADA, reservation.getStatus());
    }

    @Test
    void shouldRefusePurposeLongerThanFiveHundredCharacters() {
        ReservationRequest request = new ReservationRequest(ROOM_ID, nextMonday, LocalTime.of(10, 0),
                LocalTime.of(11, 0), "a".repeat(501));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertEquals("A finalidade deve ter no máximo 500 caracteres", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldLockRoomBeforeCheckingConflicts() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());
        when(reservationRepository.findConflicting(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(userService.getUserById(PROFESSOR_ID))
                .thenReturn(new UserResponse(PROFESSOR_ID, "Maria", "maria@teste.com", UserRole.PROFESSOR));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserveCommonRoom(PROFESSOR_ID, request);

        InOrder inOrder = inOrder(roomService, reservationRepository);
        inOrder.verify(roomService).findRoomForReservation(ROOM_ID);
        inOrder.verify(reservationRepository).findConflicting(any(), any(), any(), any(), any());
        inOrder.verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    void shouldLockRoomBeforeChangingReservationStatus() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());

        service.rejectRequest(COORDINATOR_ID, 5L, "Sem disponibilidade");

        InOrder inOrder = inOrder(roomService, reservationRepository);
        inOrder.verify(roomService).findRoomForReservation(ROOM_ID);
        inOrder.verify(reservationRepository).findById(5L);
    }

    @Test
    void shouldRefuseSpecialRequestForCommonRoom() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requestSpecialRoom(PROFESSOR_ID, request));

        assertEquals("Essa sala não é especial e pode ser reservada diretamente", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldCreatePendingRequestWhenProfessorAsksForSpecialRoom() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, request.startTime(), request.endTime(),
                ReservationStatus.CONFIRMADA)).thenReturn(List.of());
        when(userService.getUserById(PROFESSOR_ID))
                .thenReturn(new UserResponse(PROFESSOR_ID, "Maria", "maria@teste.com", UserRole.PROFESSOR));
        when(reservationRepository.existsOverlappingByResponsible(PROFESSOR_ID, ROOM_ID, nextMonday,
                request.startTime(), request.endTime(), ReservationStatus.PENDENTE)).thenReturn(false);
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            saved.setId(50L);
            return saved;
        });

        ReservationResponse response = service.requestSpecialRoom(PROFESSOR_ID, request);

        assertEquals(50L, response.id());
        assertEquals(ReservationStatus.PENDENTE, response.status());
        assertNull(response.evaluatorName());
        verify(reservationRepository, never()).findConflicting(any(), any(), any(), any(), eq(ReservationStatus.PENDENTE));
    }

    @Test
    void shouldRefuseSecondOverlappingPendingRequestFromSameResponsible() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());
        when(reservationRepository.findConflicting(any(), any(), any(), any(), any())).thenReturn(List.of());
        when(userService.getUserById(PROFESSOR_ID))
                .thenReturn(new UserResponse(PROFESSOR_ID, "Maria", "maria@teste.com", UserRole.PROFESSOR));
        when(reservationRepository.existsOverlappingByResponsible(any(), any(), any(), any(), any(), any()))
                .thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requestSpecialRoom(PROFESSOR_ID, request));

        assertEquals("Você já tem uma solicitação pendente para essa sala nesse horário", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRefuseSpecialRequestOverlappingConfirmedReservation() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, request.startTime(), request.endTime(),
                ReservationStatus.CONFIRMADA)).thenReturn(List.of(new Reservation()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.requestSpecialRoom(PROFESSOR_ID, request));

        assertEquals("Já existe uma reserva para essa sala nesse dia e horário", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldAutoApproveSpecialRequestFromManagementAndCancelOverlappingPendingOnes() {
        ReservationRequest request = request(LocalTime.of(10, 0), LocalTime.of(11, 0));
        Reservation otherPending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 30)), 60L);
        when(holidayService.findHolidayDescription(nextMonday)).thenReturn(Optional.empty());
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, request.startTime(), request.endTime(),
                ReservationStatus.CONFIRMADA)).thenReturn(List.of());
        when(userService.getUserById(COORDINATOR_ID))
                .thenReturn(new UserResponse(COORDINATOR_ID, "Ana", "ana@teste.com", UserRole.COORDENACAO));
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, request.startTime(),
                request.endTime(), ReservationStatus.PENDENTE)).thenReturn(List.of(otherPending));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(51L);
            }
            return saved;
        });

        ReservationResponse response = service.requestSpecialRoom(COORDINATOR_ID, request);

        assertEquals(ReservationStatus.CONFIRMADA, response.status());
        assertEquals("Ana", response.evaluatorName());
        assertEquals(ReservationStatus.CANCELADA, otherPending.getStatus());
        assertEquals(CancellationReason.HORARIO_APROVADO_PARA_OUTRA, otherPending.getCancellationReason());
        verify(reservationRepository, never()).existsOverlappingByResponsible(any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void shouldApproveRequestAndCancelOverlappingPendingOnes() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        Reservation competing = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 30)), 6L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, pending.getStartTime(),
                pending.getEndTime(), ReservationStatus.PENDENTE)).thenReturn(List.of(pending, competing));
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, pending.getStartTime(),
                pending.getEndTime(), ReservationStatus.CONFIRMADA)).thenReturn(List.of());

        service.approveRequest(COORDINATOR_ID, 5L);

        assertEquals(ReservationStatus.CONFIRMADA, pending.getStatus());
        assertEquals(COORDINATOR_ID, pending.getEvaluatedById());
        assertNotNull(pending.getEvaluatedAt());
        assertNull(pending.getCancellationReason());
        assertEquals(ReservationStatus.CANCELADA, competing.getStatus());
        assertEquals(CancellationReason.HORARIO_APROVADO_PARA_OUTRA, competing.getCancellationReason());
    }

    @Test
    void shouldNotApproveWhenSlotWasConfirmedMeanwhile() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, pending.getStartTime(),
                pending.getEndTime(), ReservationStatus.PENDENTE)).thenReturn(List.of(pending));
        when(reservationRepository.findConflicting(ROOM_ID, nextMonday, pending.getStartTime(),
                pending.getEndTime(), ReservationStatus.CONFIRMADA)).thenReturn(List.of(new Reservation()));

        assertThrows(IllegalStateException.class, () -> service.approveRequest(COORDINATOR_ID, 5L));

        assertEquals(ReservationStatus.PENDENTE, pending.getStatus());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldNotApproveRequestThatIsNoLongerPending() {
        Reservation rejected = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        rejected.setStatus(ReservationStatus.RECUSADA);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(rejected));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.approveRequest(COORDINATOR_ID, 5L));

        assertEquals("Essa solicitação não está mais pendente", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void shouldRejectRequestWithJustification() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());

        service.rejectRequest(COORDINATOR_ID, 5L, "  Laboratório em manutenção  ");

        assertEquals(ReservationStatus.RECUSADA, pending.getStatus());
        assertEquals("Laboratório em manutenção", pending.getRejectionJustification());
        assertEquals(COORDINATOR_ID, pending.getEvaluatedById());
        assertNotNull(pending.getEvaluatedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectRequestWithoutJustification(String justification) {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());

        service.rejectRequest(COORDINATOR_ID, 5L, justification);

        assertEquals(ReservationStatus.RECUSADA, pending.getStatus());
        assertNull(pending.getRejectionJustification());
        assertEquals(COORDINATOR_ID, pending.getEvaluatedById());
    }

    @Test
    void shouldRefuseJustificationLongerThanFiveHundredCharacters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.rejectRequest(COORDINATOR_ID, 5L, "a".repeat(501)));

        assertEquals("A justificativa deve ter no máximo 500 caracteres", ex.getMessage());
    }

    @Test
    void shouldExpireRequestAfterSeventyTwoHours() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        pending.setCreatedAt(Instant.now().minus(Duration.ofHours(73)));
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());

        service.expireRequest(5L);

        assertEquals(ReservationStatus.EXPIRADA, pending.getStatus());
    }

    @Test
    void shouldExpireRequestWhenRequestedTimeHasStarted() {
        Reservation pending = pendingAt(LocalDateTime.now().minusMinutes(1), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());

        service.expireRequest(5L);

        assertEquals(ReservationStatus.EXPIRADA, pending.getStatus());
    }

    @Test
    void shouldNotExpireRecentRequestForFutureTime() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class, () -> service.expireRequest(5L));

        assertEquals(ReservationStatus.PENDENTE, pending.getStatus());
    }

    @Test
    void shouldLetResponsibleCancelOwnPendingRequest() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(specialRoom());

        service.cancelOwnReservation(PROFESSOR_ID, 5L);

        assertEquals(ReservationStatus.CANCELADA, pending.getStatus());
        assertEquals(CancellationReason.PELO_RESPONSAVEL, pending.getCancellationReason());
    }

    @Test
    void shouldNotCheckInPendingRequest() {
        Reservation pending = pendingAt(LocalDateTime.now().minusMinutes(5), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.checkIn(PROFESSOR_ID, 5L));

        assertEquals("Essa solicitação ainda aguarda avaliação", ex.getMessage());
        assertNull(pending.getCheckInAt());
    }

    @Test
    void shouldNotCancelPendingRequestByManagement() {
        Reservation pending = pendingAt(LocalDateTime.of(nextMonday, LocalTime.of(10, 0)), 5L);
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class, () -> service.cancelReservationByManagement(5L));

        assertEquals(ReservationStatus.PENDENTE, pending.getStatus());
    }

    private Reservation pendingAt(LocalDateTime start, Long id) {
        return Reservation.builder().id(id).roomId(ROOM_ID).responsibleId(PROFESSOR_ID)
                .date(start.toLocalDate()).startTime(start.toLocalTime()).endTime(start.toLocalTime().plusHours(1))
                .purpose("Aula prática").status(ReservationStatus.PENDENTE).createdAt(Instant.now()).build();
    }

    private Reservation reservationAt(LocalDateTime start) {
        return Reservation.builder().id(5L).roomId(ROOM_ID).responsibleId(PROFESSOR_ID)
                .date(start.toLocalDate()).startTime(start.toLocalTime()).endTime(start.toLocalTime().plusHours(1))
                .purpose("Class").build();
    }

    @Test
    void shouldBlockDatesMoreThanTwoYearsAhead() {
        LocalDate beyondLimit = LocalDate.now().plusYears(2).plusDays(1);

        assertThat(service.findUnavailableDayReason(beyondLimit))
                .contains("Só é possível reservar com até 2 anos de antecedência");
    }

    @Test
    void shouldNotApplyAdvanceLimitOnTheLastAllowedDate() {
        assertThat(service.findUnavailableDayReason(LocalDate.now().plusYears(2))
                .filter(reason -> reason.contains("antecedência")))
                .isEmpty();
    }

    @Test
    void shouldRefuseReservationBeyondAdvanceLimit() {
        ReservationRequest request = new ReservationRequest(ROOM_ID, LocalDate.of(9999, 12, 29), LocalTime.of(10, 0),
                LocalTime.of(11, 0), "Usar a sala");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.reserveCommonRoom(PROFESSOR_ID, request));

        assertEquals("Só é possível reservar com até 2 anos de antecedência", ex.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void blocksSundayAlways() {
        assertThat(service.findUnavailableDayReason(SUNDAY)).contains("Não há reservas aos domingos");
    }

    @Test
    void blocksSaturdayOnlyWhenConfigured() {
        when(holidayService.findHolidayDescription(any())).thenReturn(Optional.empty());
        ReservationService saturdayAllowed = new ReservationService(reservationRepository, roomService, userService,
                holidayService, false);

        assertThat(service.findUnavailableDayReason(SATURDAY)).contains("Não há reservas aos sábados");
        assertThat(saturdayAllowed.findUnavailableDayReason(SATURDAY)).isEmpty();
    }

    @Test
    void blocksHolidaysFromTheLocalCopy() {
        when(holidayService.findHolidayDescription(CORPUS_CHRISTI))
                .thenReturn(Optional.of("Corpus Christi (feriado municipal)"));

        assertThat(service.findUnavailableDayReason(CORPUS_CHRISTI))
                .contains("Não é possível reservar em 04/06/2026: Corpus Christi (feriado municipal)");
    }

    @Test
    void allowsRegularWeekday() {
        when(holidayService.findHolidayDescription(WEDNESDAY)).thenReturn(Optional.empty());

        assertThat(service.findUnavailableDayReason(WEDNESDAY)).isEmpty();
    }

    private ReservationRequest request(LocalTime start, LocalTime end) {
        return new ReservationRequest(ROOM_ID, nextMonday, start, end, "Usar a sala para revisar estudos");
    }

    private RoomResponse commonRoom() {
        return new RoomResponse(ROOM_ID, "Sala 12", "SALA", "Predio 1", 30, "Uma sala muito legal", true, false,
                Set.of(), List.of(new TimeRange("08:00", "18:00")));
    }

    private RoomResponse specialRoom() {
        return new RoomResponse(ROOM_ID, "Laboratório 3", "LABORATORIO", "Predio 2", 20, "Laboratório de química",
                true, true, Set.of(), List.of(new TimeRange("08:00", "18:00")));
    }

}
