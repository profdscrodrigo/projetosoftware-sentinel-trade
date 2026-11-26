package br.mackenzie.sentineltrade.controller;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.controller.form.PrecoForm;
import br.mackenzie.sentineltrade.model.Administrador;
import br.mackenzie.sentineltrade.service.MercadoService;
import br.mackenzie.sentineltrade.service.OrdemService;
import br.mackenzie.sentineltrade.service.RegraNegocioException;

/* Telas do administrador: mercado simulado, recuperação e auditoria (RF-11, RF-13, RF-17) */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final MercadoService mercado;
    private final OrdemService ordens;
    private final AuditoriaService auditoria;

    public AdminController(MercadoService mercado, OrdemService ordens, AuditoriaService auditoria) {
        this.mercado = mercado;
        this.ordens = ordens;
        this.auditoria = auditoria;
    }

    @GetMapping
    public String painel(Model model) {
        model.addAttribute("ativos", mercado.listarAtivos());
        model.addAttribute("mercadoAberto", mercado.isMercadoAberto());
        model.addAttribute("bolsaDisponivel", mercado.isBolsaDisponivel());
        model.addAttribute("ordensComFalha", ordens.listarComFalha());
        return "admin/painel";
    }

    @PostMapping("/ativos/{id}/cotacao")
    public String atualizarCotacao(@AuthenticationPrincipal Administrador admin, @PathVariable("id") long id,
                                   @Valid @ModelAttribute PrecoForm form, BindingResult resultado,
                                   RedirectAttributes flash) {
        if (resultado.hasErrors()) {
            flash.addFlashAttribute("erro", "Cotação inválida. Use um valor entre R$ 0,01 e R$ 1.000.000,00.");
            return "redirect:/admin";
        }
        try {
            int executadas = mercado.atualizarCotacao(admin, id, form.getPreco());
            flash.addFlashAttribute("sucesso", "Cotação atualizada. Ordens executadas com o novo preço: " + executadas + ".");
        } catch (RegraNegocioException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/parametros")
    public String alterarParametro(@AuthenticationPrincipal Administrador admin,
                                   @RequestParam("chave") String chave, @RequestParam("valor") String valor,
                                   RedirectAttributes flash) {
        try {
            mercado.alterarParametro(admin, chave, valor);
            flash.addFlashAttribute("sucesso", "Parâmetro atualizado.");
        } catch (RegraNegocioException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/admin";
    }

    @PostMapping("/recuperar")
    public String recuperar(@AuthenticationPrincipal Administrador admin, RedirectAttributes flash) {
        int recuperadas = ordens.recuperarFalhas(admin);
        if (recuperadas > 0) {
            flash.addFlashAttribute("sucesso", recuperadas + " ordem(ns) recuperada(s) e reenviada(s) à Bolsa.");
        } else {
            flash.addFlashAttribute("aviso", "Nenhuma ordem foi recuperada. Confira se a Bolsa está disponível.");
        }
        return "redirect:/admin";
    }

    @GetMapping("/auditoria")
    public String auditoria(Model model) {
        model.addAttribute("registros", auditoria.listarRecentes(100));
        return "admin/auditoria";
    }
}
