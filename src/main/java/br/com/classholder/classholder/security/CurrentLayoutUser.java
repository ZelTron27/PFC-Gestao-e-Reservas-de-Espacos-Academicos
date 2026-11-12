package br.com.classholder.classholder.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.UserService;

@ControllerAdvice
public class CurrentLayoutUser {

    private final UserService userService;

    public CurrentLayoutUser(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("usuario")
    public UserResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }

        try {
            return userService.getUserByEmail(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
