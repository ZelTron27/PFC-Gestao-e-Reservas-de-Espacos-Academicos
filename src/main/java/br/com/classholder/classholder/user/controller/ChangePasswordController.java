package br.com.classholder.classholder.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/senha")
public class ChangePasswordController {

    private final UserService userService;

    public ChangePasswordController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/trocar")
    public String showForm() {
        return "senha/trocar";
    }

    @PostMapping("/trocar")
    public String changePassword(Authentication authentication, @RequestParam("senhaAtual") String currentPassword,
            @RequestParam("novaSenha") String newPassword, @RequestParam("confirmarSenha") String confirmPassword,
            Model model) {
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("erro", "A confirmação não confere com a nova senha.");
            return "senha/trocar";
        }

        if (!userService.changePassword(authentication.getName(), currentPassword, newPassword)) {
            model.addAttribute("erro", "Senha atual incorreta.");
            return "senha/trocar";
        }

        return "redirect:/";
    }

}
