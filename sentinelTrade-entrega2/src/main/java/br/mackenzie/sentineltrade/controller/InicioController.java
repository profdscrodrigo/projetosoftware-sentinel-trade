package br.mackenzie.sentineltrade.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import br.mackenzie.sentineltrade.model.Usuario;

/* Página inicial: manda cada usuário para a sua área */
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio(@AuthenticationPrincipal Usuario usuario) {
        return usuario == null ? "redirect:/login" : "redirect:" + usuario.getPaginaInicial();
    }

    @GetMapping("/acesso-negado")
    public String acessoNegado() {
        return "acesso-negado";
    }
}
