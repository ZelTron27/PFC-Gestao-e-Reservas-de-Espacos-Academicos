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
    public String exibirConfiguracao(Authentication authentication, Model model) {
        String email = authentication.getName();

        if (!userService.hasAcceptedLgpdTerm(email)) {
            return "redirect:/lgpd";
        }
        if (!userService.isFirstLogin(email)) {
            return "redirect:/";
        }

        adicionarQrCodeAoModelo(email, model);
        return "totp/configurar";
    }

    @PostMapping("/configurar")
    public String confirmarConfiguracao(Authentication authentication, @RequestParam String codigo,
            Model model, HttpSession session) {
        String email = authentication.getName();
        Integer codigoNumerico = parseCodigo(codigo);

        if (codigoNumerico == null || !userService.confirmarConfiguracaoDoisFatores(email, codigoNumerico)) {
            adicionarQrCodeAoModelo(email, model);
            model.addAttribute("erro", "Código inválido. Tente novamente.");
            return "totp/configurar";
        }

        session.setAttribute(SecuritySessionAttributes.TOTP_VERIFICADO, Boolean.TRUE);
        return "redirect:/";
    }

    @GetMapping("/verificar")
    public String exibirVerificacao(Authentication authentication) {
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
    public String confirmarVerificacao(Authentication authentication, @RequestParam String codigo,
            Model model, HttpSession session) {
        String email = authentication.getName();
        Integer codigoNumerico = parseCodigo(codigo);

        if (codigoNumerico == null || !userService.verificarCodigoDoisFatores(email, codigoNumerico)) {
            model.addAttribute("erro", "Código inválido. Tente novamente.");
            return "totp/verificar";
        }

        session.setAttribute(SecuritySessionAttributes.TOTP_VERIFICADO, Boolean.TRUE);
        return "redirect:/";
    }

    private void adicionarQrCodeAoModelo(String email, Model model) {
        String segredo = userService.getOrCreateTwoFactorSecret(email);
        model.addAttribute("qrCodeBase64", totpService.gerarQrCodeBase64(email, segredo));
        model.addAttribute("segredo", segredo);
    }

    private Integer parseCodigo(String codigo) {
        try {
            return Integer.valueOf(codigo.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
