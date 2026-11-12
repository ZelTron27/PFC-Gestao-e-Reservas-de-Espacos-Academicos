package br.com.classholder.classholder.room.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDateTime;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

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
class RoomControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoomRepository roomRepository;

    @Test
    void list_asAdmin_shouldReturn200WithRoomInList() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        Room room = createRoom();

        MvcResult result = mockMvc.perform(get("/salas")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("salas/lista");
        @SuppressWarnings("unchecked")
        List<RoomResponse> rooms = (List<RoomResponse>) result.getModelAndView().getModel().get("salas");
        assertThat(rooms).extracting(RoomResponse::roomName).contains(room.getRoomName());
        assertThat(result.getResponse().getContentAsString()).contains(room.getRoomName());
    }

    @Test
    void newForm_asCoordenacao_shouldReturn200WithEmptyForm() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);

        MvcResult result = mockMvc.perform(get("/salas/nova")
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("salas/formulario");
        var model = result.getModelAndView().getModel();
        assertThat(model.get("modoEdicao")).isEqualTo(false);
        assertThat(model.get("sala")).isInstanceOf(RoomFormData.class);
        assertThat(((RoomFormData) model.get("sala")).getRoomName()).isNull();
        assertThat(model).containsKey("equipamentos");
        assertThat(result.getResponse().getContentAsString())
                .contains("action=\"/salas\"")
                .contains("name=\"roomName\"");
    }

    @Test
    void editForm_withExistingId_shouldReturn200WithRoomData() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        Room room = createRoom();

        MvcResult result = mockMvc.perform(get("/salas/{id}/editar", room.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("salas/formulario");
        var model = result.getModelAndView().getModel();
        assertThat(model.get("modoEdicao")).isEqualTo(true);
        assertThat(model.get("salaId")).isEqualTo(room.getId());
        RoomFormData form = (RoomFormData) model.get("sala");
        assertThat(form.getRoomName()).isEqualTo(room.getRoomName());
        assertThat(form.getRoomCapacity()).isEqualTo(room.getRoomCapacity());
        assertThat(form.getLocalization()).isEqualTo(room.getLocalization());
        assertThat(result.getResponse().getContentAsString())
                .contains("value=\"" + room.getRoomName() + "\"")
                .contains("action=\"/salas/" + room.getId() + "\"");
    }

    @Test
    void update_withValidData_shouldRedirectAndPersistChanges() throws Exception {
        User coordenacao = createUser(UserRole.COORDENACAO);
        Room room = createRoom();
        String newName = "Sala Editada " + System.nanoTime();
        MultiValueMap<String, String> params = buildRoomParams(newName, "Predio Z", 55);
        params.add("active", "false");

        MvcResult result = mockMvc.perform(post("/salas/{id}", room.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .params(params))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/salas");
        Room updated = roomRepository.findById(room.getId()).orElseThrow();
        assertThat(updated.getRoomName()).isEqualTo(newName);
        assertThat(updated.getLocalization()).isEqualTo("Predio Z");
        assertThat(updated.getRoomCapacity()).isEqualTo(55);
        assertThat(updated.getActive()).isFalse();
        assertThat(updated.getOperatingHours()).containsExactly(new TimeRange("07:00", "12:00"));
    }

    @Test
    void editForm_withNonNumericId_shouldReturn400() throws Exception {
        User admin = createUser(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(get("/salas/abc/editar")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentTypeMismatchException.class);
        assertThat(result.getModelAndView()).isNull();
    }

    @Test
    void editForm_withNonexistentId_shouldReturn404WithMessage() throws Exception {
        User admin = createUser(UserRole.ADMIN);
        long nonexistentId = Long.MAX_VALUE;

        MvcResult result = mockMvc.perform(get("/salas/{id}/editar", nonexistentId)
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getResponse().getContentAsString()).contains("Sala não encontrada");
    }

    private MultiValueMap<String, String> buildRoomParams(String name, String localization, int capacity) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("roomName", name);
        params.add("roomType", "Laboratorio");
        params.add("localization", localization);
        params.add("roomCapacity", String.valueOf(capacity));
        params.add("description", "Descricao atualizada");
        params.add("operatingHoursJson", "[{\"start\":\"07:00\",\"end\":\"12:00\"}]");
        return params;
    }

    private User createUser(UserRole role) {
        long suffix = System.nanoTime();
        return userRepository.save(User.builder()
                .name(role.name() + " Salas " + suffix)
                .email(role.name().toLowerCase() + ".salas." + suffix + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

    private Room createRoom() {
        return roomRepository.save(Room.builder()
                .roomName("Sala Original " + System.nanoTime())
                .roomType("Sala comum")
                .localization("Predio A")
                .roomCapacity(30)
                .description("Sala de teste")
                .active(true)
                .operatingHours(List.of(new TimeRange("08:00", "18:00")))
                .build());
    }

}
