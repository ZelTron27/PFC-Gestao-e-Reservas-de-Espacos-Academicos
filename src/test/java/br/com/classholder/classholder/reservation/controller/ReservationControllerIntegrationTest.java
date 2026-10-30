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

    private static final LocalDate PROXIMA_SEGUNDA = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void todas_comoProfessor_deveRetornar403() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        Room sala = criarSala(true);
        criarReserva(sala, professor, "Reserva Restrita " + System.nanoTime());

        MvcResult result = mockMvc.perform(get("/reservas/todas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(result.getModelAndView()).isNull();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Reserva Restrita");
    }

    @Test
    void criar_comSalaInexistente_deveRetornar404() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        long salaInexistente = Long.MAX_VALUE;

        MvcResult result = mockMvc.perform(post("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(salaInexistente))
                .param("date", PROXIMA_SEGUNDA.toString())
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
    void listar_comoCoordenacao_deveRetornar200ComListaDeReservas() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);
        Room sala = criarSala(true);
        Reservation reserva = criarReserva(sala, coordenacao, "Reuniao Coordenacao " + System.nanoTime());

        MvcResult result = mockMvc.perform(get("/reservas")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/lista");
        @SuppressWarnings("unchecked")
        List<ReservationResponse> reservas = (List<ReservationResponse>) result.getModelAndView().getModel()
                .get("reservas");
        assertThat(reservas).extracting(ReservationResponse::purpose).containsExactly(reserva.getPurpose());
        assertThat(result.getResponse().getContentAsString())
                .contains(sala.getRoomName(), reserva.getPurpose());
    }

    @Test
    void espacos_comoCoordenacao_deveRetornar200ComSalasAtivas() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);
        Room ativa = criarSala(true);
        Room inativa = criarSala(false);

        MvcResult result = mockMvc.perform(get("/reservas/espacos")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/espacos");
        @SuppressWarnings("unchecked")
        List<RoomResponse> salas = (List<RoomResponse>) result.getModelAndView().getModel().get("salas");
        assertThat(salas).extracting(RoomResponse::roomName)
                .contains(ativa.getRoomName())
                .doesNotContain(inativa.getRoomName());
        assertThat(result.getResponse().getContentAsString())
                .contains(ativa.getRoomName())
                .doesNotContain(inativa.getRoomName());
    }

    @Test
    void espacos_comoProfessor_deveRetornar200ComSalasAtivas() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        Room ativa = criarSala(true);
        Room inativa = criarSala(false);

        MvcResult result = mockMvc.perform(get("/reservas/espacos")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/espacos");
        @SuppressWarnings("unchecked")
        List<RoomResponse> salas = (List<RoomResponse>) result.getModelAndView().getModel().get("salas");
        assertThat(salas).extracting(RoomResponse::roomName)
                .contains(ativa.getRoomName())
                .doesNotContain(inativa.getRoomName());
        assertThat(result.getResponse().getContentAsString())
                .contains(ativa.getRoomName())
                .doesNotContain(inativa.getRoomName());
    }

    @Test
    void novaReserva_comoProfessor_deveRetornar200ComFormulario() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        Room sala = criarSala(true);

        MvcResult result = mockMvc.perform(get("/reservas/nova")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(sala.getId()))
                .param("date", PROXIMA_SEGUNDA.toString())
                .param("durationBlocks", "6"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/formulario");
        var model = result.getModelAndView().getModel();
        ReservationFormData form = (ReservationFormData) model.get("form");
        assertThat(form.getRoomId()).isEqualTo(sala.getId());
        assertThat(form.getDate()).isEqualTo(PROXIMA_SEGUNDA);
        assertThat(((RoomResponse) model.get("salaEscolhida")).roomName()).isEqualTo(sala.getRoomName());
        @SuppressWarnings("unchecked")
        List<LocalTime> horarios = (List<LocalTime>) model.get("horarios");
        assertThat(horarios).first().isEqualTo(LocalTime.of(8, 0));
        assertThat(horarios).last().isEqualTo(LocalTime.of(15, 0));
        assertThat(result.getResponse().getContentAsString()).contains(sala.getRoomName());
    }

    @Test
    void todas_comoCoordenacao_deveRetornar200ComReservasDeTodos() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);
        User professorA = criarUsuario(UserRole.PROFESSOR);
        User professorB = criarUsuario(UserRole.PROFESSOR);
        Room sala = criarSala(true);
        Reservation reservaA = criarReserva(sala, professorA, "Aula A " + System.nanoTime(), LocalTime.of(9, 0));
        Reservation reservaB = criarReserva(sala, professorB, "Aula B " + System.nanoTime(), LocalTime.of(14, 0));

        MvcResult result = mockMvc.perform(get("/reservas/todas")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("reservas/todas");
        @SuppressWarnings("unchecked")
        List<ReservationResponse> reservas = (List<ReservationResponse>) result.getModelAndView().getModel()
                .get("reservas");
        assertThat(reservas).extracting(ReservationResponse::purpose)
                .contains(reservaA.getPurpose(), reservaB.getPurpose());
        assertThat(reservas).extracting(ReservationResponse::responsibleName)
                .contains(professorA.getName(), professorB.getName());
        assertThat(result.getResponse().getContentAsString())
                .contains(professorA.getName(), professorB.getName(), reservaA.getPurpose(), reservaB.getPurpose());
    }

    @Test
    void cancelarPelaGestao_comoCoordenacao_deveRedirecionarEMarcarCanceladaPelaGestao() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);
        User professor = criarUsuario(UserRole.PROFESSOR);
        Reservation reserva = criarReserva(criarSala(true), professor, "Reserva a cancelar " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/reservas/todas/{id}/cancelar", reserva.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas/todas");
        assertThat(result.getFlashMap().get("sucesso")).isNotNull();
        assertThat(result.getFlashMap().get("erro")).isNull();
        Reservation cancelada = reservationRepository.findById(reserva.getId()).orElseThrow();
        assertThat(cancelada.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(cancelada.getCancellationReason()).isEqualTo(CancellationReason.PELA_GESTAO);
    }

    @Test
    void cancelarPelaGestao_comoProfessor_deveRetornar403() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        Reservation reserva = criarReserva(criarSala(true), professor, "Reserva protegida " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/reservas/todas/{id}/cancelar", reserva.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        Reservation intacta = reservationRepository.findById(reserva.getId()).orElseThrow();
        assertThat(intacta.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(intacta.getCancellationReason()).isNull();
    }

    @Test
    void cancelar_comoResponsavel_deveRedirecionarEMarcarCanceladaPeloResponsavel() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        Reservation reserva = criarReserva(criarSala(true), professor, "Reserva propria " + System.nanoTime());

        MvcResult result = mockMvc.perform(post("/reservas/{id}/cancelar", reserva.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        assertThat(result.getFlashMap().get("sucesso")).isNotNull();
        assertThat(result.getFlashMap().get("erro")).isNull();
        Reservation cancelada = reservationRepository.findById(reserva.getId()).orElseThrow();
        assertThat(cancelada.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(cancelada.getCancellationReason()).isEqualTo(CancellationReason.PELO_RESPONSAVEL);
    }

    @Test
    void checkIn_comoResponsavelDentroDaJanela_deveRedirecionarERegistrarCheckIn() throws Exception {
        User professor = criarUsuario(UserRole.PROFESSOR);
        LocalDateTime inicio = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
        Reservation reserva = criarReserva(criarSala(true), professor, "Aula em andamento " + System.nanoTime(),
                inicio);

        MvcResult result = mockMvc.perform(post("/reservas/{id}/check-in", reserva.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        assertThat(result.getFlashMap().get("sucesso")).isNotNull();
        assertThat(result.getFlashMap().get("erro")).isNull();
        Reservation comCheckIn = reservationRepository.findById(reserva.getId()).orElseThrow();
        assertThat(comCheckIn.getCheckInAt()).isNotNull();
        assertThat(comCheckIn.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
    }

    private User criarUsuario(UserRole role) {
        long sufixo = System.nanoTime();
        return userRepository.save(User.builder()
                .name(role.name() + " Teste " + sufixo)
                .email(role.name().toLowerCase() + "." + sufixo + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

    private Room criarSala(boolean ativa) {
        return roomRepository.save(Room.builder()
                .roomName((ativa ? "Sala Ativa " : "Sala Inativa ") + System.nanoTime())
                .roomType("Sala comum")
                .localization("Predio A")
                .roomCapacity(30)
                .description("Sala de teste")
                .active(ativa)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
    }

    private Reservation criarReserva(Room sala, User responsavel, String finalidade) {
        return criarReserva(sala, responsavel, finalidade, LocalTime.of(10, 0));
    }

    private Reservation criarReserva(Room sala, User responsavel, String finalidade, LocalTime inicio) {
        return criarReserva(sala, responsavel, finalidade, LocalDateTime.of(PROXIMA_SEGUNDA, inicio));
    }

    private Reservation criarReserva(Room sala, User responsavel, String finalidade, LocalDateTime inicio) {
        return reservationRepository.save(Reservation.builder()
                .roomId(sala.getId())
                .responsibleId(responsavel.getId())
                .date(inicio.toLocalDate())
                .startTime(inicio.toLocalTime())
                .endTime(inicio.toLocalTime().plusHours(1))
                .purpose(finalidade)
                .build());
    }

}
