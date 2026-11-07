package br.com.classholder.classholder.auditquery.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import br.com.classholder.classholder.auditquery.service.AuditViewService;

@Controller
public class AuditController {

    private final AuditViewService auditViewService;

    public AuditController(AuditViewService auditViewService) {
        this.auditViewService = auditViewService;
    }

    @GetMapping("/auditoria")
    public String showAudit(
            @RequestParam(name = "de", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "ate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "usuario", required = false) String user,
            @RequestParam(name = "sala", required = false) Long roomId,
            @RequestParam(name = "acao", required = false) String action,
            @RequestParam(name = "pagina", defaultValue = "1") int page,
            Model model) {
        model.addAttribute("auditoria", auditViewService.buildView(from, to, user, roomId, action, page));
        return "auditoria/lista";
    }

}
