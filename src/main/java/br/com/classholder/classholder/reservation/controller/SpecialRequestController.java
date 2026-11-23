package br.com.classholder.classholder.reservation.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.classholder.classholder.reservation.dto.PendingListView;
import br.com.classholder.classholder.reservation.service.ReservationListService;
import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/solicitacoes")
public class SpecialRequestController {

    private final ReservationService reservationService;
    private final UserService userService;
    private final ReservationListService reservationListService;

    public SpecialRequestController(ReservationService reservationService, UserService userService,
            ReservationListService reservationListService) {
        this.reservationService = reservationService;
        this.userService = userService;
        this.reservationListService = reservationListService;
    }

    @GetMapping
    public String listPending(
            @RequestParam(name = "de", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "ate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "sala", required = false) Long roomId,
            @RequestParam(name = "responsavel", required = false) String responsible,
            @RequestParam(name = "pagina", defaultValue = "1") int page, Model model) {
        PendingListView view = reservationListService.buildPending(reservationService.listPendingRequests(), from,
                to, roomId, responsible, page);
        model.addAttribute("grupos", view.page().rows());
        model.addAttribute("pagina", view.page());
        model.addAttribute("lista", view);
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
