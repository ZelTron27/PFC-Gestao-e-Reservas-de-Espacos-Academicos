package br.com.classholder.classholder.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.CancellationReason;
import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.dto.ReservationResponse;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.dto.RoomResponse;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationControllerIntegrationTest {

    private static final LocalDate NEXT_MONDAY = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void listAll_asProfessor_shouldReturn403() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Room room = createRoom(true);
        createReservation(room, professor, "Reserva Restrita " + System.nanoTime());

        MvcResult result = mockMvc.perform(get("/reservas/todas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.getModelAndView()).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Reserva Restrita");
    }

    @Test
    void create_withNonexistentRoom_shouldReturn404WithMessage() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        long nonexistentRoomId = Long.MAX_VALUE;

        MvcResult result = mockMvc.perform(post("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(nonexistentRoomId))
                .param("date", NEXT_MONDAY.toString())
                .param("startTime", "10:00")
                .param("durationBlocks", "2")
                .param("purpose", "Sala que nao existe"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getResponse().getContentAsString()).contains("Sala não encontrada");
        assertThat(reservationRepository.findAllByResponsibleIdOrderByDateDescStartTimeDesc(professor.getId()))
                .isEmpty();
    }

    @Test
    void list_asCoordenacao_shouldReturn200WithOwnReservations() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room room = createRoom(true);
        Reservation reservation = createReservation(room, coordenacao, "Reuniao Coordenacao " + System.nanoTime());

        MvcResult result = mockMvc.perform(get("/reservas")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/lista");
        @SuppressWarnings("unchecked")
        List<ReservationResponse> reservations = (List<ReservationResponse>) result.getModelAndView().getModel()
                .get("reservas");
        assertThat(reservations).extracting(ReservationResponse::purpose).containsExactly(reservation.getPurpose());
        assertThat(result.getResponse().getContentAsString())
                .contains(room.getRoomName(), reservation.getPurpose());
    }

    @Test
    void spaces_asCoordenacao_shouldReturn200WithActiveRoomsOnly() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room active = createRoom(true);
        Room inactive = createRoom(false);

        MvcResult result = mockMvc.perform(get("/reservas/espacos")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/espacos");
        @SuppressWarnings("unchecked")
        List<RoomResponse> rooms = (List<RoomResponse>) result.getModelAndView().getModel().get("salas");
        assertThat(rooms).extracting(RoomResponse::roomName)
                .contains(active.getRoomName())
                .doesNotContain(inactive.getRoomName());
        assertThat(result.getResponse().getContentAsString())
                .contains(active.getRoomName())
                .doesNotContain(inactive.getRoomName());
    }

    @Test
    void spaces_asProfessor_shouldReturn200WithActiveRoomsOnly() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Room active = createRoom(true);
        Room inactive = createRoom(false);

        MvcResult result = mockMvc.perform(get("/reservas/espacos")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/espacos");
        @SuppressWarnings("unchecked")
        List<RoomResponse> rooms = (List<RoomResponse>) result.getModelAndView().getModel().get("salas");
        assertThat(rooms).extracting(RoomResponse::roomName)
                .contains(active.getRoomName())
                .doesNotContain(inactive.getRoomName());
        assertThat(result.getResponse().getContentAsString())
                .contains(active.getRoomName())
                .doesNotContain(inactive.getRoomName());
    }

    @Test
    void newForm_asProfessor_shouldReturn200WithAvailableStartTimes() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Room room = createRoom(true);

        MvcResult result = mockMvc.perform(get("/reservas/nova")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(room.getId()))
                .param("date", NEXT_MONDAY.toString())
                .param("durationBlocks", "6"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/formulario");
        var model = result.getModelAndView().getModel();
        ReservationFormData form = (ReservationFormData) model.get("form");
        assertThat(form.getRoomId()).isEqualTo(room.getId());
        assertThat(form.getDate()).isEqualTo(NEXT_MONDAY);
        assertThat(((RoomResponse) model.get("salaEscolhida")).roomName()).isEqualTo(room.getRoomName());
        @SuppressWarnings("unchecked")
        List<LocalTime> startTimes = (List<LocalTime>) model.get("horarios");
        assertThat(startTimes).first().isEqualTo(LocalTime.of(8, 0));
        assertThat(startTimes).last().isEqualTo(LocalTime.of(15, 0));
        assertThat(result.getResponse().getContentAsString()).contains(room.getRoomName());
    }

    @Test
    void listAll_asCoordenacao_shouldReturn200WithEveryonesReservations() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        User professorA = createUser(UserRole.PROFESSOR);
        User professorB = createUser(UserRole.PROFESSOR);
        Room room = createRoom(true);
        Reservation reservationA = createReservation(room, professorA, "Aula A " + System.nanoTime(), LocalTime.of(9, 0));
        Reservation reservationB = createReservation(room, professorB, "Aula B " + System.nanoTime(), LocalTime.of(14, 0));

        MvcResult result = mockMvc.perform(get("/reservas/todas")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/todas");
        @SuppressWarnings("unchecked")
        List<ReservationResponse> reservations = (List<ReservationResponse>) result.getModelAndView().getModel()
                .get("reservas");
        assertThat(reservations).extracting(ReservationResponse::purpose)
                .contains(reservationA.getPurpose(), reservationB.getPurpose());
        assertThat(reservations).extracting(ReservationResponse::responsibleName)
                .contains(professorA.getName(), professorB.getName());
        assertThat(result.getResponse().getContentAsString())
                .contains(professorA.getName(), professorB.getName(), reservationA.getPurpose(), reservationB.getPurpose());
    }

    @Test
    void cancelByManagement_asCoordenacao_shouldRedirectAndMarkCancelledByManagement() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        User professor = createUser(UserRole.PROFESSOR);
        Reservation reservation = createReservation(createRoom(true), professor, "Reserva a cancelar " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/reservas/todas/{id}/cancelar", reservation.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas/todas");
        assertThat(result.getFlashMap().get("sucesso")).isNotNull();
        assertThat(result.getFlashMap().get("erro")).isNull();
        Reservation cancelled = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(cancelled.getCancellationReason()).isEqualTo(CancellationReason.PELA_GESTAO);
    }

    @Test
    void cancelByManagement_asProfessor_shouldReturn403() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Reservation reservation = createReservation(createRoom(true), professor, "Reserva protegida " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/reservas/todas/{id}/cancelar", reservation.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        Reservation untouched = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(untouched.getCancellationReason()).isNull();
    }

    @Test
    void cancelOwn_asResponsible_shouldRedirectAndMarkCancelledByResponsible() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Reservation reservation = createReservation(createRoom(true), professor, "Reserva propria " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/reservas/{id}/cancelar", reservation.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        assertThat(result.getFlashMap().get("sucesso")).isNotNull();
        assertThat(result.getFlashMap().get("erro")).isNull();
        Reservation cancelled = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(cancelled.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(cancelled.getCancellationReason()).isEqualTo(CancellationReason.PELO_RESPONSAVEL);
    }

    @Test
    void checkIn_asResponsibleWithinWindow_shouldRedirectAndRecordCheckIn() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        LocalDateTime start = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
        Reservation reservation = createReservation(createRoom(true), professor, "Aula em andamento " + System.nanoTime(),
                start);

        MvcResult result = mockMvc.perform(post("/reservas/{id}/check-in", reservation.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        assertThat(result.getFlashMap().get("sucesso")).isNotNull();
        assertThat(result.getFlashMap().get("erro")).isNull();
        Reservation withCheckIn = reservationRepository.findById(reservation.getId()).orElseThrow();
        assertThat(withCheckIn.getCheckInAt()).isNotNull();
        assertThat(withCheckIn.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
    }

    private User createUser(UserRole role) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder()
                .name(role.name() + " Teste " + suffix)
                .email(role.name().toLowerCase() + "." + suffix + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

    private Room createRoom(boolean active) {
        return roomRepository.save(Room.builder()
                .roomName((active ? "Sala Ativa " : "Sala Inativa ") + System.nanoTime())
                .roomType("Sala comum")
                .localization("Predio A")
                .roomCapacity(30)
                .description("Sala de teste")
                .active(active)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
    }

    private Reservation createReservation(Room room, User responsible, String purpose) {
        return createReservation(room, responsible, purpose, LocalTime.of(10, 0));
    }

    private Reservation createReservation(Room room, User responsible, String purpose, LocalTime start) {
        return createReservation(room, responsible, purpose, LocalDateTime.of(NEXT_MONDAY, start));
    }

    private Reservation createReservation(Room room, User responsible, String purpose, LocalDateTime start) {
        return reservationRepository.save(Reservation.builder()
                .roomId(room.getId())
                .responsibleId(responsible.getId())
                .date(start.toLocalDate())
                .startTime(start.toLocalTime())
                .endTime(start.toLocalTime().plusHours(1))
                .purpose(purpose)
                .build());
    }

}
