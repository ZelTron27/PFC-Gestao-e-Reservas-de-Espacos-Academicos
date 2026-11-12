package br.com.classholder.classholder.audit.aop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.repository.AuditLogRepository;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    private static final Long AUTHOR_ID = 7L;

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(auditLogRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        AuditContext.clear();
    }

    @Test
    void shouldRecordAuthorIdWhenUserIsAuthenticated() throws Exception {
        authenticateAs(AUTHOR_ID);
        AuditContext.setEntidadeId(3L);
        AuditContext.setNome("Sala 12");

        aspect.audit(auditableOf("roomAction"));

        AuditLog recorded = recordedLog();
        assertThat(recorded.getUserId()).isEqualTo(AUTHOR_ID);
        assertThat(recorded.getEntidadeId()).isEqualTo(3L);
        assertThat(recorded.getEntidadeNome()).isEqualTo("Sala 12");
    }

    @Test
    void shouldRecordNullAuthorWhenThereIsNoAuthentication() throws Exception {
        AuditContext.setEntidadeId(3L);

        aspect.audit(auditableOf("roomAction"));

        assertThat(recordedLog().getUserId()).isNull();
    }

    @Test
    void shouldRecordNullAuthorWhenPrincipalIsAnonymous() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken("key", "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        aspect.audit(auditableOf("roomAction"));

        assertThat(recordedLog().getUserId()).isNull();
    }

    @Test
    void shouldDiscardNameWhenEntityIsUserEvenIfCallerSetsIt() throws Exception {
        authenticateAs(AUTHOR_ID);
        AuditContext.setEntidadeId(9L);
        AuditContext.setNome("Maria (maria@teste.com)");

        aspect.audit(auditableOf("userAction"));

        AuditLog recorded = recordedLog();
        assertThat(recorded.getEntidadeId()).isEqualTo(9L);
        assertThat(recorded.getEntidadeNome()).isNull();
        assertThat(AuditContext.consumeNome()).isNull();
    }

    private void authenticateAs(Long userId) {
        AuditPrincipal principal = () -> userId;
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of()));
    }

    private AuditLog recordedLog() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private Auditable auditableOf(String methodName) throws NoSuchMethodException {
        return AuditAspectTest.class.getDeclaredMethod(methodName).getAnnotation(Auditable.class);
    }

    @Auditable(acao = "SALA_CRIADA", entidadeTipo = "SALA")
    private void roomAction() {
    }

    @Auditable(acao = "USUARIO_CRIADO", entidadeTipo = "USUARIO")
    private void userAction() {
    }

}
