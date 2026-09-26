package br.com.classholder.classholder.security;

import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

import br.com.classholder.classholder.user.service.UserService;

@Component
public class LoginAttemptListener {

    private final UserService userService;

    public LoginAttemptListener(UserService userService) {
        this.userService = userService;
    }

    @EventListener
    public void onAuthenticationFailure(AuthenticationFailureBadCredentialsEvent event) {
        userService.registerFailedLogin(event.getAuthentication().getName());
    }

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        userService.registerSuccessfulLogin(event.getAuthentication().getName());
    }

}
