package br.com.classholder.classholder.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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
class UserListIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @Test
    void adminSeesDeactivateButNotEditForAnotherAdmin() throws Exception {
        User actor = save(UserRole.ADMIN);
        User other = save(UserRole.ADMIN);

        mockMvc.perform(get("/usuarios")
                .with(user(actor.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/usuarios/" + other.getId() + "/desativar")))
                .andExpect(content().string(not(containsString("/usuarios/" + other.getId() + "/editar"))));
    }

    @Test
    void coordinatorSeesNoActionForAnAdmin() throws Exception {
        User coordinator = save(UserRole.COORDENACAO);
        User admin = save(UserRole.ADMIN);

        mockMvc.perform(get("/usuarios")
                .with(user(coordinator.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/usuarios/" + admin.getId() + "/desativar"))))
                .andExpect(content().string(not(containsString("/usuarios/" + admin.getId() + "/editar"))));
    }

    @Test
    void coordinatorPostingDeactivationOfAnAdminIsRefused() throws Exception {
        User coordinator = save(UserRole.COORDENACAO);
        User admin = save(UserRole.ADMIN);

        mockMvc.perform(post("/usuarios/{id}/desativar", admin.getId())
                .with(user(coordinator.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("erro", "Você não tem permissão para alterar esse usuário"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminSeesAnonymizeForOthersButNotForItself() throws Exception {
        User actor = save(UserRole.ADMIN);
        User otherAdmin = save(UserRole.ADMIN);
        User professor = save(UserRole.PROFESSOR);

        mockMvc.perform(get("/usuarios")
                .with(user(actor.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/usuarios/" + professor.getId() + "/anonimizar")))
                .andExpect(content().string(containsString("/usuarios/" + otherAdmin.getId() + "/anonimizar")))
                .andExpect(content().string(not(containsString("/usuarios/" + actor.getId() + "/anonimizar"))))
                .andExpect(content().string(containsString("Esta operação não pode ser desfeita.")));
    }

    @Test
    void coordinatorDoesNotSeeTheAnonymizeButton() throws Exception {
        User coordinator = save(UserRole.COORDENACAO);
        User professor = save(UserRole.PROFESSOR);

        mockMvc.perform(get("/usuarios")
                .with(user(coordinator.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/usuarios/" + professor.getId() + "/anonimizar"))));
    }

    @Test
    void coordinatorPostingAnonymizationIsForbidden() throws Exception {
        User coordinator = save(UserRole.COORDENACAO);
        User professor = save(UserRole.PROFESSOR);

        mockMvc.perform(post("/usuarios/{id}/anonimizar", professor.getId())
                .with(user(coordinator.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findById(professor.getId()).orElseThrow().isAnonymized()).isFalse();
    }

    @Test
    void adminConfirmingAnonymizationAnonymizesAndShowsSuccess() throws Exception {
        User actor = save(UserRole.ADMIN);
        User professor = save(UserRole.PROFESSOR);

        mockMvc.perform(post("/usuarios/{id}/anonimizar", professor.getId())
                .with(user(actor.getEmail()).roles("ADMIN"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("sucesso", "Usuário anonimizado"));

        assertThat(userRepository.findById(professor.getId()).orElseThrow().isAnonymized()).isTrue();
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
