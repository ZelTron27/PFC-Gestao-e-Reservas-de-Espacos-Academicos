package br.com.classholder.classholder.user.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.classholder.classholder.security.SecuritySessionAttributes;
import br.com.classholder.classholder.user.service.TotpService;
import br.com.classholder.classholder.user.service.UserService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/totp")
public class TotpController {

    private final UserService userService;
    private final TotpService totpService;

    public TotpController(UserService userService, TotpService totpService) {
        this.userService = userService;
        this.totpService = totpService;
    }

    @GetMapping("/configurar")
    public String showSetupForm(Authentication authentication, Model model) {
        String email = authentication.getName();

        if (!userService.hasAcceptedLgpdTerm(email)) {
            return "redirect:/lgpd";
        }
        if (!userService.isFirstLogin(email)) {
            return "redirect:/";
        }

        addQrCodeToModel(email, model);
        return "totp/configurar";
    }

    @PostMapping("/configurar")
    public String confirmSetup(Authentication authentication, @RequestParam("codigo") String code,
            Model model, HttpSession session) {
        String email = authentication.getName();
        Integer numericCode = parseCode(code);

        if (numericCode == null || !userService.confirmTwoFactorSetup(email, numericCode)) {
            addQrCodeToModel(email, model);
            model.addAttribute("erro", "Código inválido. Tente novamente.");
            return "totp/configurar";
        }

        session.setAttribute(SecuritySessionAttributes.TOTP_VERIFIED, Boolean.TRUE);
        return "redirect:/";
    }

    @GetMapping("/verificar")
    public String showVerificationForm(Authentication authentication) {
        String email = authentication.getName();

        if (!userService.hasAcceptedLgpdTerm(email)) {
            return "redirect:/lgpd";
        }
        if (userService.isFirstLogin(email)) {
            return "redirect:/totp/configurar";
        }

        return "totp/verificar";
    }

    @PostMapping("/verificar")
    public String confirmVerification(Authentication authentication, @RequestParam("codigo") String code,
            Model model, HttpSession session) {
        String email = authentication.getName();
        Integer numericCode = parseCode(code);

        if (numericCode == null || !userService.verifyTwoFactorCode(email, numericCode)) {
            model.addAttribute("erro", "Código inválido. Tente novamente.");
            return "totp/verificar";
        }

        session.setAttribute(SecuritySessionAttributes.TOTP_VERIFIED, Boolean.TRUE);
        return "redirect:/";
    }

    private void addQrCodeToModel(String email, Model model) {
        String secret = userService.getOrCreateTwoFactorSecret(email);
        model.addAttribute("qrCodeBase64", totpService.generateQrCodeBase64(email, secret));
        model.addAttribute("segredo", secret);
    }

    private Integer parseCode(String code) {
        try {
            return Integer.valueOf(code.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
