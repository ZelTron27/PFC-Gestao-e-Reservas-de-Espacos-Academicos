package br.com.classholder.classholder.reservation.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.classholder.classholder.reservation.dto.ReservationRequest;
import br.com.classholder.classholder.reservation.service.ReservationService;
import br.com.classholder.classholder.room.service.RoomService;
import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/reservas")
public class ReservationController {

    private final ReservationService reservationService;
    private final RoomService roomService;
    private final UserService userService;

    public ReservationController(ReservationService reservationService, RoomService roomService,
            UserService userService) {
        this.reservationService = reservationService;
        this.roomService = roomService;
        this.userService = userService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        Long professorId = professorId(authentication);
        model.addAttribute("reservas", reservationService.listReservationsByProfessor(professorId));
        return "reservas/lista";
    }

    @GetMapping("/nova")
    public String showForm(@RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        ReservationFormData form = new ReservationFormData();
        form.setRoomId(roomId);
        form.setDate(date);

        model.addAttribute("form", form);
        model.addAttribute("salas", roomService.listActiveCommonRooms());

        if (roomId != null && date != null) {
            model.addAttribute("salaEscolhida", roomService.findRoomById(roomId));
            model.addAttribute("horarios", reservationService.listAvailableStartTimes(roomId, date));
        }

        return "reservas/formulario";
    }

    @PostMapping
    public String reserve(Authentication authentication, @ModelAttribute("form") @Valid ReservationFormData form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return showFormWithHorarios(form, model);
        }

        try {
            reservationService.reserveCommonRoom(professorId(authentication), toRequest(form));
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("erro", e.getMessage());
            return showFormWithHorarios(form, model);
        }

        return "redirect:/reservas";
    }

    private String showFormWithHorarios(ReservationFormData form, Model model) {
        model.addAttribute("salas", roomService.listActiveCommonRooms());
        if (form.getRoomId() != null && form.getDate() != null) {
            model.addAttribute("salaEscolhida", roomService.findRoomById(form.getRoomId()));
            model.addAttribute("horarios", reservationService.listAvailableStartTimes(form.getRoomId(), form.getDate()));
        }
        return "reservas/formulario";
    }

    @GetMapping("/todas")
    public String listAll(Model model) {
        model.addAttribute("reservas", reservationService.listAllReservations());
        return "reservas/todas";
    }

    @PostMapping("/{id}/cancelar")
    public String cancel(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            reservationService.cancelReservation(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/reservas/todas";
    }

    private Long professorId(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName()).id();
    }

    private ReservationRequest toRequest(ReservationFormData form) {
        return new ReservationRequest(form.getRoomId(), form.getDate(), form.getStartTime(),
                form.getStartTime().plusMinutes(form.getDurationBlocks() * 30L), form.getPurpose());
    }

}
