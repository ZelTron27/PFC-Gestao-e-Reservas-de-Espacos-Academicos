package br.com.classholder.classholder.reservation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
import br.com.classholder.classholder.reservation.dto.PendingRequestGroup;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
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
class SpecialRequestControllerIntegrationTest {

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
    void listPending_asProfessor_shouldReturn403() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);

        MvcResult result = mockMvc.perform(get("/solicitacoes")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void approve_asProfessor_shouldReturn403AndKeepRequestPending() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Room room = createSpecialRoom();
        Reservation pending = requestAs(professor, room, "10:00");

        MvcResult result = mockMvc.perform(post("/solicitacoes/{id}/aprovar", pending.getId())
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(reservationRepository.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(ReservationStatus.PENDENTE);
    }

    @Test
    void approve_asCoordenacao_shouldConfirmRequestAndCancelCompetingOnes() throws Exception {
        User first = createUser(UserRole.PROFESSOR);
        User second = createUser(UserRole.PROFESSOR);
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room room = createSpecialRoom();
        Reservation chosen = requestAs(first, room, "10:00");
        Reservation competing = requestAs(second, room, "10:30");

        MvcResult listResult = mockMvc.perform(get("/solicitacoes")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();
        @SuppressWarnings("unchecked")
        List<PendingRequestGroup> groups = (List<PendingRequestGroup>) listResult.getModelAndView().getModel()
                .get("grupos");
        assertThat(groups).filteredOn(group -> group.roomId().equals(room.getId()))
                .singleElement()
                .satisfies(group -> assertThat(group.requests()).hasSize(2));

        mockMvc.perform(post("/solicitacoes/{id}/aprovar", chosen.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        Reservation approved = reservationRepository.findById(chosen.getId()).orElseThrow();
        Reservation displaced = reservationRepository.findById(competing.getId()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(approved.getEvaluatedById()).isEqualTo(coordenacao.getId());
        assertThat(displaced.getStatus()).isEqualTo(ReservationStatus.CANCELADA);
        assertThat(displaced.getCancellationReason()).isEqualTo(CancellationReason.HORARIO_APROVADO_PARA_OUTRA);
    }

    @Test
    void reject_withoutJustification_shouldRejectRequest() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room room = createSpecialRoom();
        Reservation pending = requestAs(professor, room, "14:00");

        MvcResult result = mockMvc.perform(post("/solicitacoes/{id}/recusar", pending.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("justificativa", "  "))
                .andReturn();

        assertThat(result.getFlashMap().get("sucesso")).isEqualTo("Solicitação recusada");
        Reservation rejected = reservationRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ReservationStatus.RECUSADA);
        assertThat(rejected.getRejectionJustification()).isNull();
        assertThat(rejected.getEvaluatedById()).isEqualTo(coordenacao.getId());
    }

    @Test
    void reject_withJustification_shouldStoreItAndTheEvaluator() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room room = createSpecialRoom();
        Reservation pending = requestAs(professor, room, "14:00");

        mockMvc.perform(post("/solicitacoes/{id}/recusar", pending.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("justificativa", "Laboratório reservado para manutenção"))
                .andReturn();

        Reservation rejected = reservationRepository.findById(pending.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(ReservationStatus.RECUSADA);
        assertThat(rejected.getRejectionJustification()).isEqualTo("Laboratório reservado para manutenção");
        assertThat(rejected.getEvaluatedById()).isEqualTo(coordenacao.getId());

        MvcResult ownList = mockMvc.perform(get("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();
        assertThat(ownList.getResponse().getContentAsString())
                .contains("Recusada", "Justificativa: Laboratório reservado para manutenção",
                        "Avaliada por " + coordenacao.getName());

        MvcResult allList = mockMvc.perform(get("/reservas/todas")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();
        assertThat(allList.getResponse().getContentAsString())
                .contains("Avaliador", coordenacao.getName());
    }

    @Test
    void form_forSpecialRoom_shouldOfferRequestInsteadOfReservation() throws Exception {
        User professor = createUser(UserRole.PROFESSOR);
        Room room = createSpecialRoom();
        Reservation pending = requestAs(professor, room, "09:00");

        MvcResult form = mockMvc.perform(get("/reservas/nova")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(room.getId()))
                .param("date", NEXT_MONDAY.toString()))
                .andReturn();
        assertThat(form.getResponse().getContentAsString())
                .contains("Solicitar sala especial", "Enviar solicitação", "expira em 72 horas");

        MvcResult ownList = mockMvc.perform(get("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();
        assertThat(ownList.getResponse().getContentAsString())
                .contains("Aguardando avaliação", "Cancelar essa solicitação?",
                        "/reservas/" + pending.getId() + "/cancelar");
    }

    @Test
    void request_asCoordenacao_shouldBeConfirmedWithoutGoingToApproval() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room room = createSpecialRoom();

        Reservation reservation = requestAs(coordenacao, room, "16:00", "COORDENACAO");

        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        assertThat(reservation.getEvaluatedById()).isEqualTo(coordenacao.getId());
    }

    private Reservation requestAs(User user, Room room, String startTime) throws Exception {
        return requestAs(user, room, startTime, "PROFESSOR");
    }

    private Reservation requestAs(User requester, Room room, String startTime, String role) throws Exception {
        MvcResult result = mockMvc.perform(post("/reservas")
                .with(user(requester.getEmail()).roles(role))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(room.getId()))
                .param("date", NEXT_MONDAY.toString())
                .param("startTime", startTime)
                .param("durationBlocks", "2")
                .param("purpose", "Aula prática"))
                .andReturn();

        assertThat(result.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        return reservationRepository.findAllByResponsibleIdOrderByDateDescStartTimeDesc(requester.getId()).stream()
                .filter(reservation -> reservation.getRoomId().equals(room.getId()))
                .findFirst()
                .orElseThrow();
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

    private Room createSpecialRoom() {
        return roomRepository.save(Room.builder()
                .roomName("Laboratório " + System.nanoTime())
                .roomType("Laboratório")
                .localization("Predio B")
                .roomCapacity(20)
                .description("Sala especial de teste")
                .active(true)
                .salaEspecial(true)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
    }

}
