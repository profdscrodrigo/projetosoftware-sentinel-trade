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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.mackenzie.sentineltrade.controller.form.LimiteForm;
import br.mackenzie.sentineltrade.model.AnalistaRisco;
import br.mackenzie.sentineltrade.service.ContaService;
import br.mackenzie.sentineltrade.service.OrdemService;
import br.mackenzie.sentineltrade.service.RegraNegocioException;

/* Tela do analista de risco: ordens rejeitadas e limites (RF-05, RF-18) */
@Controller
@RequestMapping("/risco")
public class RiscoController {

    private final ContaService contas;
    private final OrdemService ordens;

    public RiscoController(ContaService contas, OrdemService ordens) {
        this.contas = contas;
        this.ordens = ordens;
    }

    @GetMapping
    public String painel(Model model) {
        model.addAttribute("contas", contas.listarContasComLimites());
        model.addAttribute("rejeitadas", ordens.listarRejeitadas());
        return "risco/painel";
    }

    @PostMapping("/contas/{id}/limites")
    public String alterarLimites(@AuthenticationPrincipal AnalistaRisco analista, @PathVariable("id") long contaId,
                                 @Valid @ModelAttribute LimiteForm form, BindingResult resultado,
                                 RedirectAttributes flash) {
        if (resultado.hasErrors()) {
            flash.addFlashAttribute("erro", "Limites inválidos. Use valores entre R$ 1,00 e R$ 10.000.000,00.");
            return "redirect:/risco";
        }
        try {
            contas.alterarLimites(analista, contaId, form.paraLimite());
            flash.addFlashAttribute("sucesso", "Limites atualizados.");
        } catch (RegraNegocioException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/risco";
    }
}
