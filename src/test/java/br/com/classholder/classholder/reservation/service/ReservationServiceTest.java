package br.com.classholder.classholder.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.classholder.classholder.holiday.service.HolidayService;
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
        assertEquals("Maria", response.professorName());
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
        Reservation confirmed = Reservation.builder().id(3L).roomId(ROOM_ID).professorId(PROFESSOR_ID)
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
    void shouldCancelConfirmedReservation() {
        Reservation reservation = Reservation.builder().id(5L).roomId(ROOM_ID).professorId(PROFESSOR_ID)
                .date(nextMonday).startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(11, 0)).purpose("Class")
                .build();
        when(reservationRepository.findById(5L)).thenReturn(Optional.of(reservation));
        when(roomService.findRoomById(ROOM_ID)).thenReturn(commonRoom());

        service.cancelReservation(5L);

        assertEquals(ReservationStatus.CANCELADA, reservation.getStatus());
        verify(reservationRepository).save(reservation);
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

}
