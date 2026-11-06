package br.com.classholder.classholder.audit.aop;

import org.springframework.security.core.Authentication;

public interface AuditPrincipal {

    Long getUserId();

    static Long userIdOf(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof AuditPrincipal principal
                ? principal.getUserId()
                : null;
    }

}
