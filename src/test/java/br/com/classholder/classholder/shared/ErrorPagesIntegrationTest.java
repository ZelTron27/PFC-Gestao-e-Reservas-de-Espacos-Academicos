package br.com.classholder.classholder.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;

import com.warrenstrange.googleauth.GoogleAuthenticator;

import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.domain.User;
import br.com.classholder.classholder.user.repository.UserRepository;
import br.com.classholder.classholder.user.service.TotpService;

@Tag("integration")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(ErrorPagesIntegrationTest.FailingController.class)
class ErrorPagesIntegrationTest {

    private static final String PASSWORD = "Senha@12345";
    private static final String SECRET_EXCEPTION_MESSAGE = "detalhe-interno-sigiloso";
    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\"\\s+value=\"([^\"]+)\"");

    @Controller
    static class FailingController {

        @GetMapping("/teste-erro/500")
        String fail() {
            throw new IllegalStateException(SECRET_EXCEPTION_MESSAGE);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private TotpService totpService;

    private RestClient restClient;

    @BeforeEach
    void setUp() {
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        restClient = RestClient.builder()
                .baseUrl("http://localhost:" + port)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @Test
    void forbiddenRoute_asProfessor_shouldReturn403WithOwnPage() {
        String session = loginAs(UserRole.PROFESSOR);

        ResponseEntity<String> response = get("/auditoria", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).isTrue();
        assertThat(response.getBody())
                .contains("403")
                .contains("Acesso negado. Você não tem permissão para acessar esta página.")
                .contains("Voltar ao início")
                .contains("href=\"/\"");
        assertNoTechnicalDetails(response.getBody());
    }

    @Test
    void unknownUrl_asAuthenticatedUser_shouldReturn404WithOwnPage() {
        String session = loginAs(UserRole.PROFESSOR);

        ResponseEntity<String> response = get("/rota-que-nao-existe", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .contains("404")
                .contains("Página não encontrada.")
                .contains("Voltar ao início")
                .contains("href=\"/\"");
        assertNoTechnicalDetails(response.getBody());
    }

    @Test
    void unexpectedException_shouldReturn500WithOwnPageAndNoDetails() {
        String session = loginAs(UserRole.PROFESSOR);

        ResponseEntity<String> response = get("/teste-erro/500", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .contains("500")
                .contains("Ocorreu um erro inesperado. Tente novamente em instantes.")
                .contains("Voltar ao início")
                .contains("href=\"/\"")
                .doesNotContain(SECRET_EXCEPTION_MESSAGE)
                .doesNotContain("IllegalStateException");
        assertNoTechnicalDetails(response.getBody());
    }

    @Test
    void forbiddenRoute_asCoordenacao_shouldAlsoUseOwnPage() {
        String session = loginAs(UserRole.COORDENACAO);

        ResponseEntity<String> response = get("/auditoria", session);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("Acesso negado.");
        assertNoTechnicalDetails(response.getBody());
    }

    @Test
    void unknownUrl_withoutAuthentication_shouldKeepRedirectingToLogin() {
        ResponseEntity<String> response = get("/rota-que-nao-existe", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getFirst(HttpHeaders.LOCATION)).endsWith("/login");
    }

    @Test
    void errorPage_shouldNotBeServedAsWhitelabel() {
        String session = loginAs(UserRole.PROFESSOR);

        ResponseEntity<String> response = get("/error", session);

        assertThat(response.getBody()).doesNotContainIgnoringCase("whitelabel");
        assertNoTechnicalDetails(response.getBody());
    }

    private void assertNoTechnicalDetails(String body) {
        assertThat(body)
                .doesNotContainIgnoringCase("whitelabel")
                .doesNotContain("Exception")
                .doesNotContain("at org.")
                .doesNotContain("at br.com")
                .doesNotContain("br.com.classholder")
                .doesNotContain("org.springframework")
                .doesNotContain("java.lang")
                .doesNotContain("/auditoria")
                .doesNotContain("trace");
    }

    private ResponseEntity<String> get(String path, String session) {
        RestClient.RequestHeadersSpec<?> request = restClient.get().uri(path).accept(MediaType.TEXT_HTML);
        if (session != null) {
            request = request.header(HttpHeaders.COOKIE, session);
        }
        return request.retrieve().toEntity(String.class);
    }

    private String loginAs(UserRole role) {
        String secret = new GoogleAuthenticator().createCredentials().getKey();
        String email = "erro." + System.nanoTime() + "@classholder.com";
        userRepository.save(User.builder().name("Usuario Erro").email(email)
                .passwordHash(passwordEncoder.encode(PASSWORD)).role(role).firstLogin(false)
                .mustChangePassword(false).lgpdAcceptedAt(LocalDateTime.now())
                .twoFactorSecret(totpService.encrypt(secret)).build());

        ResponseEntity<String> loginPage = restClient.get().uri("/login").retrieve().toEntity(String.class);
        String anonymousSession = sessionCookie(loginPage);

        MultiValueMap<String, String> credentials = new LinkedMultiValueMap<>();
        credentials.add("username", email);
        credentials.add("password", PASSWORD);
        credentials.add("_csrf", csrfToken(loginPage.getBody()));
        ResponseEntity<String> loginResponse = restClient.post().uri("/login")
                .header(HttpHeaders.COOKIE, anonymousSession)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(credentials).retrieve().toEntity(String.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        String session = sessionCookie(loginResponse);

        ResponseEntity<String> totpPage = get("/totp/verificar", session);
        assertThat(totpPage.getStatusCode()).isEqualTo(HttpStatus.OK);

        MultiValueMap<String, String> totp = new LinkedMultiValueMap<>();
        totp.add("codigo", String.format("%06d", new GoogleAuthenticator().getTotpPassword(secret)));
        totp.add("_csrf", csrfToken(totpPage.getBody()));
        ResponseEntity<String> totpResponse = restClient.post().uri("/totp/verificar")
                .header(HttpHeaders.COOKIE, session)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(totp).retrieve().toEntity(String.class);
        assertThat(totpResponse.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(totpResponse.getHeaders().getFirst(HttpHeaders.LOCATION)).doesNotContain("totp");
        return session;
    }

    private String sessionCookie(ResponseEntity<?> response) {
        String cookie = response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .findFirst().orElseThrow();
        return cookie.substring(0, cookie.indexOf(';'));
    }

    private String csrfToken(String html) {
        Matcher matcher = CSRF.matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

}
