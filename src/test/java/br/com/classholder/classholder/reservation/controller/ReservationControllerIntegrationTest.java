package br.com.classholder.classholder.reservation.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.domain.Reservation;
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
class ReservationControllerIntegrationTest {

    private static final String EMAIL = "professor.integracao@classholder.com";
    private static final String ROOM_NAME = "Sala Integracao 101";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private ReservationRepository reservationRepository;

    private User professor;
    private Room room;

    @BeforeEach
    void setUp() {
        professor = userRepository.save(User.builder()
                .name("Professora Integracao")
                .email(EMAIL)
                .passwordHash("hash")
                .role(UserRole.PROFESSOR)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
        room = roomRepository.save(Room.builder()
                .roomName(ROOM_NAME)
                .roomType("Sala comum")
                .localization("Predio A")
                .roomCapacity(30)
                .description("Sala de teste")
                .active(true)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
    }

    @Test
    void shouldReturn200AndListProfessorReservationsWhenProfessorAccessesReservations() throws Exception {
        saveReservation("Aula de Integracao");

        MvcResult result = mockMvc.perform(get("/reservas")
                .with(user(EMAIL).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(view().name("reservas/lista"))
                .andExpect(model().attributeExists("reservas"))
                .andExpect(content().string(containsString(ROOM_NAME)))
                .andExpect(content().string(containsString("Aula de Integracao")))
                .andReturn();

        List<?> reservas = (List<?>) result.getModelAndView().getModel().get("reservas");
        assertEquals(1, reservas.size());
    }

    @Test
    void shouldReturn403WhenProfessorAccessesAllReservations() throws Exception {
        saveReservation("Reserva Restrita");

        MvcResult result = mockMvc.perform(get("/reservas/todas")
                .with(user(EMAIL).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString("Reserva Restrita"))))
                .andReturn();

        assertNull(result.getModelAndView());
    }

    private Reservation saveReservation(String purpose) {
        return reservationRepository.save(Reservation.builder()
                .roomId(room.getId())
                .professorId(professor.getId())
                .date(LocalDate.now().plusDays(7))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(11, 0))
                .purpose(purpose)
                .build());
    }

}
