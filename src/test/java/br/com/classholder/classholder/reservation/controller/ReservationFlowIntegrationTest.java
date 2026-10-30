package br.com.classholder.classholder.reservation.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.reservation.ReservationStatus;
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
class ReservationFlowIntegrationTest {

    private static final String EMAIL = "professor.fluxo@classholder.com";

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
                .name("Professor Fluxo")
                .email(EMAIL)
                .passwordHash("hash")
                .role(UserRole.PROFESSOR)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
        room = roomRepository.save(Room.builder()
                .roomName("Sala Fluxo 202")
                .roomType("Sala comum")
                .localization("Predio B")
                .roomCapacity(40)
                .description("Sala do fluxo completo")
                .active(true)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
    }

    @Test
    void shouldPersistAndDisplayReservationWhenProfessorCreatesIt() throws Exception {
        LocalDate nextMonday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        mockMvc.perform(post("/reservas")
                .with(user(EMAIL).roles("PROFESSOR"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .param("roomId", String.valueOf(room.getId()))
                .param("date", nextMonday.toString())
                .param("startTime", "10:00")
                .param("durationBlocks", "2")
                .param("purpose", "Fluxo Completo"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/reservas"));

        List<Reservation> saved = reservationRepository.findAll();
        assertEquals(1, saved.size());
        Reservation reservation = saved.get(0);
        assertEquals(professor.getId(), reservation.getResponsibleId());
        assertEquals(LocalTime.of(10, 0), reservation.getStartTime());
        assertEquals(LocalTime.of(11, 0), reservation.getEndTime());
        assertEquals(ReservationStatus.CONFIRMADA, reservation.getStatus());

        String html = mockMvc.perform(get("/reservas")
                .with(user(EMAIL).roles("PROFESSOR"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("Fluxo Completo"));
    }

}
