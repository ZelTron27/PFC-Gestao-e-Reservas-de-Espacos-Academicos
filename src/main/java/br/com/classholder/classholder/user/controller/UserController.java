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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.classholder.classholder.user.UserRole;
import br.com.classholder.classholder.user.dto.UserRequest;
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
        model.addAttribute("usuario", new UserFormData());
        model.addAttribute("perfisDisponiveis", availableRoles(authentication));
        return "usuarios/formulario";
    }

    @PostMapping
    public String create(Authentication authentication, @ModelAttribute("usuario") @Valid UserFormData form,
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

    private UserRole creatorRole(Authentication authentication) {
        return userService.getUserByEmail(authentication.getName()).role();
    }

    private List<UserRole> availableRoles(Authentication authentication) {
        if (creatorRole(authentication) == UserRole.ADMIN) {
            return Arrays.asList(UserRole.values());
        }
        return List.of(UserRole.COORDENACAO, UserRole.PROFESSOR, UserRole.ALUNO);
    }

    private UserRequest toRequest(UserFormData form) {
        return new UserRequest(form.getName(), form.getEmail(), form.getPassword(), form.getRole());
    }

}
