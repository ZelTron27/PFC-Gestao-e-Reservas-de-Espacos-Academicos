package br.com.classholder.classholder.shared;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

@ControllerAdvice
public class RecursoNaoEncontradoAdvice {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handle(RecursoNaoEncontradoException e, Model model) {
        model.addAttribute("mensagem", e.getMessage());
        return "erro/nao-encontrado";
    }
}
