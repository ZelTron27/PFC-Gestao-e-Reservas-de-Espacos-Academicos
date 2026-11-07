package br.com.classholder.classholder.holiday.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HolidayControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void coordenacaoSeesThePanelWithoutTheSyncButton() throws Exception {
        User coordenacao = save(UserRole.COORDENACAO);

        mockMvc.perform(get("/feriados")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Sincronizar agora"))));
    }

    @Test
    void coordenacaoCannotTriggerTheManualSync() throws Exception {
        User coordenacao = save(UserRole.COORDENACAO);

        mockMvc.perform(post("/feriados/sincronizar")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesTheSyncButton() throws Exception {
        User admin = save(UserRole.ADMIN);

        mockMvc.perform(get("/feriados")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sincronizar agora")));
    }

    private User save(UserRole role) {
        return userRepository.save(User.builder()
                .name(role.name() + " " + System.nanoTime())
                .email(role.name().toLowerCase() + "." + System.nanoTime() + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

}
