package br.com.classholder.classholder.reservation.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/solicitacoes")
public class SpecialRequestController {

    private final ReservationService reservationService;
    private final UserService userService;

    public SpecialRequestController(ReservationService reservationService, UserService userService) {
        this.reservationService = reservationService;
        this.userService = userService;
    }

    @GetMapping
    public String listPending(Model model) {
        model.addAttribute("grupos", reservationService.listPendingRequests());
        return "solicitacoes/pendentes";
    }

    @PostMapping("/{id}/aprovar")
    public String approve(Authentication authentication, @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        try {
            reservationService.approveRequest(currentUserId(authentication), id);
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação aprovada");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/solicitacoes";
    }

    @PostMapping("/{id}/recusar")
    public String reject(Authentication authentication, @PathVariable Long id,
            @RequestParam(required = false) String justificativa, RedirectAttributes redirectAttributes) {
        try {
            reservationService.rejectRequest(currentUserId(authentication), id, justificativa);
            redirectAttributes.addFlashAttribute("sucesso", "Solicitação recusada");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/solicitacoes";
    }

    private Long currentUserId(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName()).id();
    }

}
