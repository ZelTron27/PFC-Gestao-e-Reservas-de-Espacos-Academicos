package br.com.classholder.classholder.audit.listener;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.audit.aop.AuditPrincipal;

@Component
public class AuthenticationAuditListener {

    private final AccessAuditRecorder accessAuditRecorder;
    private final LoginUserIdResolver loginUserIdResolver;

    public AuthenticationAuditListener(AccessAuditRecorder accessAuditRecorder,
            LoginUserIdResolver loginUserIdResolver) {
        this.accessAuditRecorder = accessAuditRecorder;
        this.loginUserIdResolver = loginUserIdResolver;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        accessAuditRecorder.record("ACESSO_LOGIN_SUCESSO", AuditPrincipal.userIdOf(event.getAuthentication()));
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        accessAuditRecorder.record("ACESSO_LOGIN_FALHA",
                loginUserIdResolver.findUserIdByLogin(event.getAuthentication().getName()).orElse(null));
    }

}
