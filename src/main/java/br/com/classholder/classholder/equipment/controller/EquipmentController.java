package br.com.classholder.classholder.equipment.controller;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
        model.addAttribute("modoEdicao", false);
        model.addAttribute("form", new EquipmentFormData());
        return "equipamentos/formulario";
    }

    @GetMapping("/{id}/editar")
    public String showEditForm(@PathVariable Long id, Model model) {
        EquipmentFormData form = new EquipmentFormData();
        form.setName(equipmentService.findById(id).name());

        model.addAttribute("modoEdicao", true);
        model.addAttribute("equipamentoId", id);
        model.addAttribute("form", form);
        return "equipamentos/formulario";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id, @ModelAttribute("form") @Valid EquipmentFormData form,
            BindingResult bindingResult, Model model, RedirectAttributes redirectAttributes) {
        model.addAttribute("modoEdicao", true);
        model.addAttribute("equipamentoId", id);
        if (bindingResult.hasErrors()) {
            return "equipamentos/formulario";
        }

        try {
            equipmentService.updateEquipment(id, new EquipmentRequest(form.getName()));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            return "equipamentos/formulario";
        }

        redirectAttributes.addFlashAttribute("sucesso", "Equipamento atualizado");
        return "redirect:/equipamentos";
    }

    @PostMapping("/{id}/excluir")
    public String delete(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        equipmentService.deleteEquipment(id);
        redirectAttributes.addFlashAttribute("sucesso", "Equipamento excluído");
        return "redirect:/equipamentos";
    }

    @PostMapping
    public String create(@ModelAttribute("form") @Valid EquipmentFormData form, BindingResult bindingResult,
            Model model) {
        model.addAttribute("modoEdicao", false);
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
