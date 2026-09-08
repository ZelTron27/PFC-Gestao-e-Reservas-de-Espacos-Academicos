package br.com.classholder.classholder.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/lgpd")
public class LgpdController {

    private final UserService userService;

    public LgpdController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String exibirTermo(Authentication authentication) {
        if (userService.hasAcceptedLgpdTerm(authentication.getName())) {
            return "redirect:/";
        }
        return "lgpd/termo";
    }

    @PostMapping("/aceitar")
    public String aceitarTermo(Authentication authentication) {
        userService.acceptLgpdTerm(authentication.getName());
        return "redirect:/";
    }

}
