package br.com.classholder.classholder.room.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import br.com.classholder.classholder.equipment.service.EquipmentService;
import br.com.classholder.classholder.room.dto.RoomRequest;
import br.com.classholder.classholder.room.dto.TimeRange;
import br.com.classholder.classholder.room.service.RoomService;

@Controller
@RequestMapping("/salas")
public class RoomController {

    private static final TypeReference<List<TimeRange>> TIME_RANGE_LIST = new TypeReference<>() {
    };

    private final RoomService roomService;
    private final EquipmentService equipmentService;
    private final ObjectMapper objectMapper;

    public RoomController(RoomService roomService, EquipmentService equipmentService, ObjectMapper objectMapper) {
        this.roomService = roomService;
        this.equipmentService = equipmentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("salas", roomService.listRooms());
        return "salas/lista";
    }

    @GetMapping("/nova")
    public String showCreateForm(Model model) {
        model.addAttribute("modoEdicao", false);
        model.addAttribute("sala", new RoomFormData());
        model.addAttribute("equipamentos", equipmentService.listAll());
        return "salas/formulario";
    }

    @PostMapping
    public String create(@ModelAttribute("sala") @Valid RoomFormData form, BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("modoEdicao", false);
            model.addAttribute("equipamentos", equipmentService.listAll());
            return "salas/formulario";
        }

        try {
            roomService.createRoom(toRequest(form));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("modoEdicao", false);
            model.addAttribute("equipamentos", equipmentService.listAll());
            return "salas/formulario";
        }

        return "redirect:/salas";
    }

    @GetMapping("/{id}/editar")
    public String showEditForm(@PathVariable Long id, Model model) {
        var room = roomService.findRoomById(id);

        RoomFormData form = new RoomFormData();
        form.setRoomName(room.roomName());
        form.setRoomType(room.roomType());
        form.setLocalization(room.localization());
        form.setRoomCapacity(room.roomCapacity());
        form.setDescription(room.description());
        form.setPermiteSolicitacaoAluno(room.permiteSolicitacaoAluno());
        form.setSalaEspecial(room.salaEspecial());
        form.setActive(room.active());
        form.setEquipmentIds(room.equipmentIds());
        form.setOperatingHoursJson(writeOperatingHours(room.operatingHours()));

        model.addAttribute("modoEdicao", true);
        model.addAttribute("salaId", id);
        model.addAttribute("sala", form);
        model.addAttribute("equipamentos", equipmentService.listAll());
        return "salas/formulario";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("sala") @Valid RoomFormData form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("modoEdicao", true);
            model.addAttribute("salaId", id);
            model.addAttribute("equipamentos", equipmentService.listAll());
            return "salas/formulario";
        }

        try {
            roomService.updateRoom(id, toRequest(form));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("modoEdicao", true);
            model.addAttribute("salaId", id);
            model.addAttribute("equipamentos", equipmentService.listAll());
            return "salas/formulario";
        }

        return "redirect:/salas";
    }

    private RoomRequest toRequest(RoomFormData form) {
        return new RoomRequest(form.getRoomName(), form.getRoomType(), form.getLocalization(),
                form.getRoomCapacity(), form.getDescription(), form.isPermiteSolicitacaoAluno(),
                form.isSalaEspecial(), form.isActive(), form.getEquipmentIds(),
                readOperatingHours(form.getOperatingHoursJson()));
    }

    private List<TimeRange> readOperatingHours(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, TIME_RANGE_LIST);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Horários de funcionamento inválidos");
        }
    }

    private String writeOperatingHours(List<TimeRange> operatingHours) {
        try {
            return objectMapper.writeValueAsString(operatingHours);
        } catch (JacksonException e) {
            return "[]";
        }
    }

}
