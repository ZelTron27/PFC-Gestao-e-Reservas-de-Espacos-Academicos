package br.com.classholder.classholder.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import br.com.classholder.classholder.user.service.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class OnboardingInterceptor implements HandlerInterceptor {

    private final UserService userService;

    public OnboardingInterceptor(UserService userService) {
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return true;
        }

        String email = authentication.getName();

        if (!userService.hasAcceptedLgpdTerm(email)) {
            response.sendRedirect("/lgpd");
            return false;
        }

        if (userService.mustChangePassword(email)) {
            response.sendRedirect("/senha/trocar");
            return false;
        }

        if (userService.isFirstLogin(email)) {
            response.sendRedirect("/totp/configurar");
            return false;
        }

        Object totpVerified = request.getSession().getAttribute(SecuritySessionAttributes.TOTP_VERIFIED);
        if (!Boolean.TRUE.equals(totpVerified)) {
            response.sendRedirect("/totp/verificar");
            return false;
        }

        return true;
    }

}
