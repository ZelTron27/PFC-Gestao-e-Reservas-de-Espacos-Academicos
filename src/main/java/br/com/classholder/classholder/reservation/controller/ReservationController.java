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

import br.com.classholder.classholder.equipment.service.EquipmentService;
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
    private final EquipmentService equipmentService;

    public ReservationController(ReservationService reservationService, RoomService roomService,
            UserService userService, EquipmentService equipmentService) {
        this.reservationService = reservationService;
        this.roomService = roomService;
        this.userService = userService;
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        Long professorId = professorId(authentication);
        model.addAttribute("usuario", userService.getUserByEmail(authentication.getName()));
        model.addAttribute("reservas", reservationService.listReservationsByProfessor(professorId));
        return "reservas/lista";
    }

    @GetMapping("/espacos")
    public String espacos(Authentication authentication, Model model) {
        model.addAttribute("usuario", userService.getUserByEmail(authentication.getName()));
        model.addAttribute("salas", roomService.listRooms());
        return "reservas/espacos";
    }

    @GetMapping("/nova")
    public String showForm(Authentication authentication, @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model) {
        if (roomId == null) {
            return "redirect:/reservas/espacos";
        }

        ReservationFormData form = new ReservationFormData();
        form.setRoomId(roomId);
        form.setDate(date);

        model.addAttribute("usuario", userService.getUserByEmail(authentication.getName()));
        model.addAttribute("form", form);
        model.addAttribute("equipamentos", equipmentService.listAll());
        model.addAttribute("salaEscolhida", roomService.findRoomById(roomId));

        if (date != null) {
            model.addAttribute("horarios", reservationService.listAvailableStartTimes(roomId, date));
            model.addAttribute("diaIndisponivel", reservationService.findUnavailableDayReason(date).orElse(null));
        }

        return "reservas/formulario";
    }

    @PostMapping
    public String reserve(Authentication authentication, @ModelAttribute("form") @Valid ReservationFormData form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return showFormWithHorarios(authentication, form, model);
        }

        try {
            reservationService.reserveCommonRoom(professorId(authentication), toRequest(form));
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("erro", e.getMessage());
            return showFormWithHorarios(authentication, form, model);
        }

        return "redirect:/reservas";
    }

    private String showFormWithHorarios(Authentication authentication, ReservationFormData form, Model model) {
        model.addAttribute("usuario", userService.getUserByEmail(authentication.getName()));
        model.addAttribute("equipamentos", equipmentService.listAll());
        model.addAttribute("salaEscolhida", roomService.findRoomById(form.getRoomId()));
        if (form.getDate() != null) {
            model.addAttribute("horarios", reservationService.listAvailableStartTimes(form.getRoomId(), form.getDate()));
            model.addAttribute("diaIndisponivel",
                    reservationService.findUnavailableDayReason(form.getDate()).orElse(null));
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
