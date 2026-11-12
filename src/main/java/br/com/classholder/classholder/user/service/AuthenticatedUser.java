package br.com.classholder.classholder.user.service;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import br.com.classholder.classholder.audit.aop.AuditPrincipal;

final class AuthenticatedUser extends User implements AuditPrincipal {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    AuthenticatedUser(Long userId, String email, String passwordHash, boolean enabled,
            boolean locked,
            Collection<? extends GrantedAuthority> authorities) {
        super(email, passwordHash, enabled, true, true, !locked, authorities);
        this.userId = userId;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

}
