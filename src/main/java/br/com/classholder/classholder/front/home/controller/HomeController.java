package br.com.classholder.classholder.front.home.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.UserService;

@Controller
public class HomeController {

    private final UserService userService;

    public HomeController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/")
    public String show(Authentication authentication, Model model) {
        UserResponse usuario = userService.getUserByEmail(authentication.getName());

        if (usuario.role() == UserRole.PROFESSOR) {
            return "redirect:/reservas/espacos";
        }

        model.addAttribute("usuario", usuario);
        return "home";
    }

}
