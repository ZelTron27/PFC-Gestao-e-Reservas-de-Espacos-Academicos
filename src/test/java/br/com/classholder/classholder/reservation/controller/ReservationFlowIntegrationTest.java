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
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import br.com.classholder.classholder.reservation.ReservationStatus;
import br.com.classholder.classholder.reservation.domain.Reservation;
import br.com.classholder.classholder.reservation.repository.ReservationRepository;
import br.com.classholder.classholder.room.domain.Room;
import br.com.classholder.classholder.room.repository.RoomRepository;
import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReservationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    @Test
    void fluxo_coordenacaoCriaSalaEProfessorReserva_deveRedirecionarPersistirEExibirNaLista() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);
        User professor = criarUsuario(UserRole.PROFESSOR);
        String nomeSala = "Sala Fluxo " + System.nanoTime();
        String finalidade = "Fluxo Completo " + System.nanoTime();
        LocalDate proximaSegunda = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        MvcResult criacaoSala = criarSalaViaEndpoint(coordenacao, nomeSala);

        assertThat(criacaoSala.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(criacaoSala.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/salas");
        Room sala = roomRepository.findAll().stream()
                .filter(room -> nomeSala.equals(room.getRoomName()))
                .findFirst()
                .orElseThrow();
        assertThat(sala.getActive()).isTrue();

        MvcResult criacaoReserva = mockMvc.perform(post("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(sala.getId()))
                .param("date", proximaSegunda.toString())
                .param("startTime", "10:00")
                .param("durationBlocks", "2")
                .param("purpose", finalidade))
                .andReturn();

        assertThat(criacaoReserva.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(criacaoReserva.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        List<Reservation> reservas = reservationRepository
                .findAllByResponsibleIdOrderByDateDescStartTimeDesc(professor.getId());
        assertThat(reservas).singleElement().satisfies(reserva -> {
            assertThat(reserva.getRoomId()).isEqualTo(sala.getId());
            assertThat(reserva.getDate()).isEqualTo(proximaSegunda);
            assertThat(reserva.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(reserva.getEndTime()).isEqualTo(LocalTime.of(11, 0));
            assertThat(reserva.getPurpose()).isEqualTo(finalidade);
            assertThat(reserva.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        });

        MvcResult lista = mockMvc.perform(get("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(lista.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(lista.getModelAndView().getViewName()).isEqualTo("reservas/lista");
        assertThat(lista.getResponse().getContentAsString()).contains(nomeSala, finalidade);
    }

    private MvcResult criarSalaViaEndpoint(User autor, String nomeSala) throws Exception {
        return mockMvc.perform(post("/salas")
                .with(user(autor.getEmail()).roles(autor.getRole().name()))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .params(buildSalaParams(nomeSala)))
                .andReturn();
    }

    private MultiValueMap<String, String> buildSalaParams(String nomeSala) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("roomName", nomeSala);
        params.add("roomType", "Sala comum");
        params.add("localization", "Predio B");
        params.add("roomCapacity", "40");
        params.add("description", "Sala criada no fluxo completo");
        params.add("operatingHoursJson", "[{\"start\":\"08:00\",\"end\":\"18:00\"}]");
        return params;
    }

    private User criarUsuario(UserRole role) {
        long sufixo = System.nanoTime();
        return userRepository.save(User.builder()
                .name(role.name() + " Fluxo " + sufixo)
                .email(role.name().toLowerCase() + ".fluxo." + sufixo + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

}
