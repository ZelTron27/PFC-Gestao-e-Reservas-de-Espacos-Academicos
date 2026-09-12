package br.com.classholder.classholder.equipment.controller;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.classholder.classholder.equipment.dto.EquipmentRequest;
import br.com.classholder.classholder.equipment.service.EquipmentService;

@Controller
@RequestMapping("/equipamentos")
public class EquipmentController {

    private final EquipmentService equipmentService;

    public EquipmentController(EquipmentService equipmentService) {
        this.equipmentService = equipmentService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("equipamentos", equipmentService.listAll());
        return "equipamentos/lista";
    }

    @GetMapping("/novo")
    public String showForm(Model model) {
        model.addAttribute("form", new EquipmentFormData());
        return "equipamentos/formulario";
    }

    @PostMapping
    public String create(@ModelAttribute("form") @Valid EquipmentFormData form, BindingResult bindingResult,
            Model model) {
        if (bindingResult.hasErrors()) {
            return "equipamentos/formulario";
        }

        try {
            equipmentService.createEquipment(new EquipmentRequest(form.getName()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            return "equipamentos/formulario";
        }

        return "redirect:/equipamentos";
    }

}
