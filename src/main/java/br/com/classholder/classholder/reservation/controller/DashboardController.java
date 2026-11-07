package br.com.classholder.classholder.reservation.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.classholder.classholder.reservation.service.DashboardService;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/dashboard")
    public String showDashboard(
            @RequestParam(name = "de", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "ate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "salas", required = false) List<Long> roomIds,
            @RequestParam(name = "todas", defaultValue = "false") boolean allRooms,
            Model model) {
        model.addAttribute("painel", dashboardService.buildDashboard(from, to, roomIds, allRooms));
        return "dashboard/painel";
    }

}
