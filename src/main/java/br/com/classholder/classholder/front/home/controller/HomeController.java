package br.com.classholder.classholder.front.home.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String show() {
        return "redirect:/reservas/espacos";
    }

}
