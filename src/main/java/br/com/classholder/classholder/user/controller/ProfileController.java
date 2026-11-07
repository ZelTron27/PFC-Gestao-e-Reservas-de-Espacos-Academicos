package br.com.classholder.classholder.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.classholder.classholder.user.service.UserService;

@Controller
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/perfil")
    public String showProfile(Authentication authentication, Model model) {
        model.addAttribute("perfil", userService.getProfile(authentication.getName()));
        return "perfil/visualizar";
    }

}
