package br.com.classholder.classholder.user.controller;

import java.util.Arrays;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.dto.UserEditRequest;
import br.com.classholder.classholder.user.dto.UserRequest;
import br.com.classholder.classholder.user.dto.UserResponse;
import br.com.classholder.classholder.user.service.UserService;

@Controller
@RequestMapping("/usuarios")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("usuarios", userService.listUsers());
        return "usuarios/lista";
    }

    @GetMapping("/novo")
    public String showForm(Authentication authentication, Model model) {
        model.addAttribute("form", new UserFormData());
        model.addAttribute("perfisDisponiveis", availableRoles(authentication));
        return "usuarios/formulario";
    }

    @PostMapping
    public String create(Authentication authentication, @ModelAttribute("form") @Valid UserFormData form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("perfisDisponiveis", availableRoles(authentication));
            return "usuarios/formulario";
        }

        try {
            userService.createUser(creatorRole(authentication), toRequest(form));
        } catch (IllegalArgumentException e) {
            model.addAttribute("erro", e.getMessage());
            model.addAttribute("perfisDisponiveis", availableRoles(authentication));
            return "usuarios/formulario";
        }

        return "redirect:/usuarios";
    }

    @GetMapping("/{id}/editar")
    public String showEditForm(Authentication authentication, @PathVariable Long id, Model model,
            RedirectAttributes redirectAttributes) {
        UserResponse actor = currentUser(authentication);
        UserResponse user;
        try {
            user = userService.getManageableUser(actor.id(), actor.role(), id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
            return "redirect:/usuarios";
        }

        UserEditFormData form = new UserEditFormData();
        form.setName(user.name());
        form.setEmail(user.email());
        model.addAttribute("form", form);
        model.addAttribute("usuarioId", id);
        return "usuarios/editar";
    }

    @PostMapping("/{id}")
    public String update(Authentication authentication, @PathVariable Long id,
            @ModelAttribute("form") @Valid UserEditFormData form, BindingResult bindingResult, Model model,
            RedirectAttributes redirectAttributes) {
        model.addAttribute("usuarioId", id);
        if (bindingResult.hasErrors()) {
            return "usuarios/editar";
        }

        UserResponse actor = currentUser(authentication);
        try {
            userService.updateUser(actor.id(), actor.role(), id, new UserEditRequest(form.getName(), form.getEmail()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("erro", e.getMessage());
            return "usuarios/editar";
        }

        redirectAttributes.addFlashAttribute("sucesso", "Usuário atualizado");
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/anonimizar")
    public String anonymize(Authentication authentication, @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        UserResponse actor = currentUser(authentication);
        try {
            userService.anonymizeUser(actor.id(), actor.role(), id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário anonimizado");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/desativar")
    public String deactivate(Authentication authentication, @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        UserResponse actor = currentUser(authentication);
        try {
            userService.deactivateUser(actor.id(), actor.role(), id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário desativado");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    @PostMapping("/{id}/reativar")
    public String reactivate(Authentication authentication, @PathVariable Long id,
            RedirectAttributes redirectAttributes) {
        UserResponse actor = currentUser(authentication);
        try {
            userService.reactivateUser(actor.id(), actor.role(), id);
            redirectAttributes.addFlashAttribute("sucesso", "Usuário reativado");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/usuarios";
    }

    private UserResponse currentUser(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName());
    }

    private UserRole creatorRole(Authentication authentication) {
        return currentUser(authentication).role();
    }

    private List<UserRole> availableRoles(Authentication authentication) {
        if (creatorRole(authentication) == UserRole.ADMIN) {
            return Arrays.asList(UserRole.values());
        }
        return List.of(UserRole.COORDENACAO, UserRole.PROFESSOR);
    }

    private UserRequest toRequest(UserFormData form) {
        return new UserRequest(form.getName(), form.getEmail(), form.getPassword(), form.getRole());
    }

}
