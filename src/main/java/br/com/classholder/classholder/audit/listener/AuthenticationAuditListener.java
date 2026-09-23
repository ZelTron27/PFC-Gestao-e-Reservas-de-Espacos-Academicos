package br.com.classholder.classholder.audit.listener;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationAuditListener {

    private final AccessAuditRecorder accessAuditRecorder;

    public AuthenticationAuditListener(AccessAuditRecorder accessAuditRecorder) {
        this.accessAuditRecorder = accessAuditRecorder;
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        accessAuditRecorder.record("ACESSO_LOGIN_SUCESSO", event.getAuthentication(), extractIp(event.getAuthentication()));
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        accessAuditRecorder.record("ACESSO_LOGIN_FALHA", event.getAuthentication(), extractIp(event.getAuthentication()));
    }

    private String extractIp(Authentication authentication) {
        return authentication != null && authentication.getDetails() instanceof WebAuthenticationDetails details
                ? details.getRemoteAddress()
                : null;
    }

}
