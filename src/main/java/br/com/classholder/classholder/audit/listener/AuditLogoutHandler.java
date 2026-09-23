package br.com.classholder.classholder.audit.listener;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuditLogoutHandler implements LogoutHandler {

    private final AccessAuditRecorder accessAuditRecorder;

    public AuditLogoutHandler(AccessAuditRecorder accessAuditRecorder) {
        this.accessAuditRecorder = accessAuditRecorder;
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        if (authentication == null) {
            return;
        }

        accessAuditRecorder.record("ACESSO_LOGOUT", authentication, request.getRemoteAddr());
    }

}
