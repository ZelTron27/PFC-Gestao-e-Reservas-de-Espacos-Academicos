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
    void listar_comoAdmin_deveRetornar200ComSalaNaLista() throws Exception {
        User admin = criarUsuario(UserRole.ADMIN);
        Room sala = criarSala();

        MvcResult result = mockMvc.perform(get("/salas")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("salas/lista");
        @SuppressWarnings("unchecked")
        List<RoomResponse> salas = (List<RoomResponse>) result.getModelAndView().getModel().get("salas");
        assertThat(salas).extracting(RoomResponse::roomName).contains(sala.getRoomName());
        assertThat(result.getResponse().getContentAsString()).contains(sala.getRoomName());
    }

    @Test
    void novaSala_comoCoordenacao_deveRetornar200ComFormulario() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);

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
    void editarForm_comIdExistente_deveRetornar200ComDadosDaSala() throws Exception {
        User admin = criarUsuario(UserRole.ADMIN);
        Room sala = criarSala();

        MvcResult result = mockMvc.perform(get("/salas/{id}/editar", sala.getId())
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getModelAndView().getViewName()).isEqualTo("salas/formulario");
        var model = result.getModelAndView().getModel();
        assertThat(model.get("modoEdicao")).isEqualTo(true);
        assertThat(model.get("salaId")).isEqualTo(sala.getId());
        RoomFormData form = (RoomFormData) model.get("sala");
        assertThat(form.getRoomName()).isEqualTo(sala.getRoomName());
        assertThat(form.getRoomCapacity()).isEqualTo(sala.getRoomCapacity());
        assertThat(form.getLocalization()).isEqualTo(sala.getLocalization());
        assertThat(result.getResponse().getContentAsString())
                .contains("value=\"" + sala.getRoomName() + "\"")
                .contains("action=\"/salas/" + sala.getId() + "\"");
    }

    @Test
    void editar_comDadosValidos_deveRedirecionarEGravarAlteracoes() throws Exception {
        User coordenacao = criarUsuario(UserRole.COORDENACAO);
        Room sala = criarSala();
        String novoNome = "Sala Editada " + System.nanoTime();
        MultiValueMap<String, String> params = buildSalaParams(novoNome, "Predio Z", 55);
        params.add("active", "false");

        MvcResult result = mockMvc.perform(post("/salas/{id}", sala.getId())
                .with(user(coordenacao.getEmail()).roles("COORDENACAO"))
                .with(csrf())
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true)
                .params(params))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.FOUND.value());
        assertThat(result.getResponse().getHeader(HttpHeaders.LOCATION)).isEqualTo("/salas");
        Room atualizada = roomRepository.findById(sala.getId()).orElseThrow();
        assertThat(atualizada.getRoomName()).isEqualTo(novoNome);
        assertThat(atualizada.getLocalization()).isEqualTo("Predio Z");
        assertThat(atualizada.getRoomCapacity()).isEqualTo(55);
        assertThat(atualizada.getActive()).isFalse();
        assertThat(atualizada.getOperatingHours()).containsExactly(new TimeRange("07:00", "12:00"));
    }

    @Test
    void editarForm_comIdNaoNumerico_deveRetornar400() throws Exception {
        User admin = criarUsuario(UserRole.ADMIN);

        MvcResult result = mockMvc.perform(get("/salas/abc/editar")
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentTypeMismatchException.class);
        assertThat(result.getModelAndView()).isNull();
    }

    @Test
    void editarForm_comIdInexistente_deveRetornar404() throws Exception {
        User admin = criarUsuario(UserRole.ADMIN);
        long idInexistente = Long.MAX_VALUE;

        MvcResult result = mockMvc.perform(get("/salas/{id}/editar", idInexistente)
                .with(user(admin.getEmail()).roles("ADMIN"))
                .sessionAttr(SecuritySessionAttributes.TOTP_VERIFIED, true))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(result.getResponse().getContentAsString()).contains("Sala não encontrada");
    }

    private MultiValueMap<String, String> buildSalaParams(String nome, String localizacao, int capacidade) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("roomName", nome);
        params.add("roomType", "Laboratorio");
        params.add("localization", localizacao);
        params.add("roomCapacity", String.valueOf(capacidade));
        params.add("description", "Descricao atualizada");
        params.add("operatingHoursJson", "[{\"start\":\"07:00\",\"end\":\"12:00\"}]");
        return params;
    }

    private User criarUsuario(UserRole role) {
        long sufixo = System.nanoTime();
        return userRepository.save(User.builder()
                .name(role.name() + " Salas " + sufixo)
                .email(role.name().toLowerCase() + ".salas." + sufixo + "@classholder.com")
                .passwordHash("hash")
                .role(role)
                .firstLogin(false)
                .mustChangePassword(false)
                .lgpdAcceptedAt(LocalDateTime.now())
                .build());
    }

    private Room criarSala() {
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
