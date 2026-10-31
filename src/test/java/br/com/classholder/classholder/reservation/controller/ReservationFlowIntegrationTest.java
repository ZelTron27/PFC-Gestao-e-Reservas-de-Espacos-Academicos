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
    void flow_coordenacaoCreatesRoomAndProfessorReserves_shouldRedirectPersistAndShowInList() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        User professor = createUser(UserRole.PROFESSOR);
        String roomName = "Sala Fluxo " + System.nanoTime();
        String purpose = "Fluxo Completo " + System.nanoTime();
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        MvcResult roomCreation = createRoomViaEndpoint(coordenacao, roomName);

        assertThat(roomCreation.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(roomCreation.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/salas");
        Room room = roomRepository.findAll().stream()
                .filter(candidate -> roomName.equals(candidate.getRoomName()))
                .findFirst()
                .orElseThrow();
        assertThat(room.getActive()).isTrue();

        MvcResult reservationCreation = mockMvc.perform(post("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(room.getId()))
                .param("date", nextMonday.toString())
                .param("startTime", "10:00")
                .param("durationBlocks", "2")
                .param("purpose", purpose))
                .andReturn();

        assertThat(reservationCreation.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(reservationCreation.getResponse().getRedirectedUrl()).isEqualTo("/reservas");
        List<Reservation> reservations = reservationRepository
                .findAllByResponsibleIdOrderByDateDescStartTimeDesc(professor.getId());
        assertThat(reservations).singleElement().satisfies(reservation -> {
            assertThat(reservation.getRoomId()).isEqualTo(room.getId());
            assertThat(reservation.getDate()).isEqualTo(nextMonday);
            assertThat(reservation.getStartTime()).isEqualTo(LocalTime.of(10, 0));
            assertThat(reservation.getEndTime()).isEqualTo(LocalTime.of(11, 0));
            assertThat(reservation.getPurpose()).isEqualTo(purpose);
            assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.CONFIRMADA);
        });

        MvcResult listResult = mockMvc.perform(get("/reservas")
                .with(user(professor.getEmail()).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(listResult.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(listResult.getModelAndView().getViewName()).isEqualTo("reservas/lista");
        assertThat(listResult.getResponse().getContentAsString()).contains(roomName, purpose);
    }

    private MvcResult createRoomViaEndpoint(User author, String roomName) throws Exception {
        return mockMvc.perform(post("/salas")
                .with(user(author.getEmail()).roles(author.getRole().name()))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .params(buildRoomParams(roomName)))
                .andReturn();
    }

    private MultiValueMap<String, String> buildRoomParams(String roomName) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("roomName", roomName);
        params.add("roomType", "Sala comum");
        params.add("localization", "Predio B");
        params.add("roomCapacity", "40");
        params.add("description", "Sala criada no fluxo completo");
        params.add("operatingHoursJson", "[{\"start\":\"08:00\",\"end\":\"18:00\"}]");
        return params;
    }

    private User createUser(UserRole role) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder()
                .name(role.name() + " Fluxo " + suffix)
                .email(role.name().toLowerCase() + ".fluxo." + suffix + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

}
