package br.com.classholder.classholder.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/senha")
public class ForgotPasswordController {

    private final UserService userService;

    public ForgotPasswordController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/esqueci")
    public String showRequestForm() {
        return "senha/esqueci";
    }

    @PostMapping("/esqueci")
    public String requestReset(@RequestParam("email") String email, Model model) {
        userService.requestPasswordReset(email);

        model.addAttribute("enviado", true);
        return "senha/esqueci";
    }

    @GetMapping("/redefinir")
    public String showResetForm(@RequestParam("token") String token, Model model) {
        if (!userService.isPasswordResetTokenValid(token)) {
            model.addAttribute("tokenInvalido", true);
            return "senha/redefinir";
        }

        model.addAttribute("token", token);
        return "senha/redefinir";
    }

    @PostMapping("/redefinir")
    public String resetPassword(@RequestParam("token") String token,
            @RequestParam("novaSenha") String newPassword,
            @RequestParam("confirmarSenha") String confirmPassword,
            Model model) {
        if (!userService.isPasswordResetTokenValid(token)) {
            model.addAttribute("tokenInvalido", true);
            return "senha/redefinir";
        }

        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("erro", "A confirmação não confere com a nova senha.");
            return "senha/redefinir";
        }

        try {
            userService.resetPassword(token, newPassword);
        } catch (IllegalArgumentException e) {
            model.addAttribute("token", token);
            model.addAttribute("erro", e.getMessage());
            return "senha/redefinir";
        }

        model.addAttribute("sucesso", true);
        return "senha/redefinir";
    }

}
