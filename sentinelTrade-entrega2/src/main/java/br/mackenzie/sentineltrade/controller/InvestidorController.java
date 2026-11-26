package br.mackenzie.sentineltrade.controller;

import java.util.UUID;

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

import br.mackenzie.sentineltrade.controller.form.MensagensDeErro;
import br.mackenzie.sentineltrade.controller.form.OrdemForm;
import br.mackenzie.sentineltrade.controller.form.PrecoForm;
import br.mackenzie.sentineltrade.model.Investidor;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.StatusOrdem;
import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.notificacao.NotificacaoService;
import br.mackenzie.sentineltrade.service.ConfirmacaoAltoRiscoException;
import br.mackenzie.sentineltrade.service.ContaService;
import br.mackenzie.sentineltrade.service.MercadoService;
import br.mackenzie.sentineltrade.service.OrdemService;
import br.mackenzie.sentineltrade.service.RegraNegocioException;

/* Telas do investidor: painel, nova ordem e minhas ordens */
@Controller
@RequestMapping("/investidor")
public class InvestidorController {

    private final ContaService contas;
    private final MercadoService mercado;
    private final OrdemService ordens;
    private final NotificacaoService notificacoes;

    public InvestidorController(ContaService contas, MercadoService mercado, OrdemService ordens,
                                NotificacaoService notificacoes) {
        this.contas = contas;
        this.mercado = mercado;
        this.ordens = ordens;
        this.notificacoes = notificacoes;
    }

    /* Painel: saldo, carteira, cotações e avisos (RF-06, RF-12, RF-15) */
    @GetMapping
    public String painel(@AuthenticationPrincipal Investidor investidor, Model model) {
        model.addAttribute("saldo", contas.saldo(investidor.getContaId()));
        model.addAttribute("posicoes", contas.carteira(investidor.getContaId()));
        model.addAttribute("limite", contas.limite(investidor.getContaId()));
        model.addAttribute("ativos", mercado.listarAtivos());
        model.addAttribute("mercadoAberto", mercado.isMercadoAberto());
        model.addAttribute("notificacoes", notificacoes.recentes(investidor.getInvestidorId()));
        return "investidor/painel";
    }

    @GetMapping("/ordens/nova")
    public String novaOrdem(@AuthenticationPrincipal Investidor investidor,
                            @RequestParam(name = "ativo", required = false) String ativo,
                            @RequestParam(name = "tipo", required = false) String tipo, Model model) {
        OrdemForm form = new OrdemForm();
        form.setChaveIdempotencia(UUID.randomUUID().toString());
        form.setTipo("VENDA".equals(tipo) ? TipoOrdem.VENDA : TipoOrdem.COMPRA);
        if (ativo != null && ativo.matches("^[A-Z]{4}[0-9]{1,2}$")) {
            form.setAtivo(ativo);
        }
        return mostrarFormulario(investidor, form, model);
    }

    /* Cria a ordem (RF-07) */
    @PostMapping("/ordens")
    public String enviarOrdem(@AuthenticationPrincipal Investidor investidor,
                              @Valid @ModelAttribute("ordemForm") OrdemForm form, BindingResult resultado,
                              Model model, RedirectAttributes flash) {
        if (resultado.hasErrors()) {
            model.addAttribute("erros", MensagensDeErro.de(resultado));
            return mostrarFormulario(investidor, form, model);
        }
        try {
            Ordem ordem = ordens.criar(investidor, form.paraPedido());
            avisarResultado(ordem, flash);
            return "redirect:/investidor/ordens";
        } catch (ConfirmacaoAltoRiscoException e) {
            model.addAttribute("aviso", e.getMessage());
            model.addAttribute("pedirConfirmacao", true);
            return mostrarFormulario(investidor, form, model);
        } catch (RegraNegocioException e) {
            flash.addFlashAttribute("erro", e.getMessage());
            return "redirect:/investidor/ordens";
        }
    }

    /* Lista as ordens, com filtro opcional por status (RF-10, RF-16) */
    @GetMapping("/ordens")
    public String minhasOrdens(@AuthenticationPrincipal Investidor investidor,
                               @RequestParam(name = "status", required = false) String status, Model model) {
        var filtro = StatusOrdem.deTexto(status);
        model.addAttribute("ordens", ordens.listarDaConta(investidor.getContaId(), filtro));
        model.addAttribute("historicos", ordens.historicoDaConta(investidor.getContaId()));
        model.addAttribute("filtro", filtro.map(Enum::name).orElse("TODAS"));
        return "investidor/ordens";
    }

    /* Atualiza o preço de uma ordem aguardando execução. */
    @PostMapping("/ordens/{id}/preco")
    public String alterarPreco(@AuthenticationPrincipal Investidor investidor, @PathVariable("id") long id,
                               @Valid @ModelAttribute PrecoForm form, BindingResult resultado,
                               RedirectAttributes flash) {
        if (resultado.hasErrors()) {
            flash.addFlashAttribute("erro", "Preço inválido. Use um valor entre R$ 0,01 e R$ 1.000.000,00.");
            return "redirect:/investidor/ordens";
        }
        try {
            Ordem ordem = ordens.alterarPreco(investidor, id, form.getPreco());
            flash.addFlashAttribute("sucesso", ordem.getStatus() == StatusOrdem.EXECUTADA
                    ? "Preço alterado e ordem executada!"
                    : "Preço alterado. A ordem continua aguardando execução.");
        } catch (RegraNegocioException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/investidor/ordens";
    }

    /* Cancela uma ordem que ainda não foi executada */
    @PostMapping("/ordens/{id}/cancelar")
    public String cancelar(@AuthenticationPrincipal Investidor investidor, @PathVariable("id") long id,
                           RedirectAttributes flash) {
        try {
            ordens.cancelar(investidor, id);
            flash.addFlashAttribute("sucesso", "Ordem cancelada.");
        } catch (RegraNegocioException e) {
            flash.addFlashAttribute("erro", e.getMessage());
        }
        return "redirect:/investidor/ordens";
    }

    private String mostrarFormulario(Investidor investidor, OrdemForm form, Model model) {
        model.addAttribute("ordemForm", form);
        model.addAttribute("ativos", mercado.listarAtivos());
        model.addAttribute("saldo", contas.saldo(investidor.getContaId()));
        model.addAttribute("limite", contas.limite(investidor.getContaId()));
        return "investidor/nova-ordem";
    }

    private void avisarResultado(Ordem ordem, RedirectAttributes flash) {
        switch (ordem.getStatus()) {
            case EXECUTADA -> flash.addFlashAttribute("sucesso", "Ordem executada com sucesso!");
            case ENVIADA -> flash.addFlashAttribute("info",
                    "Ordem enviada à Bolsa. Ela será executada quando a cotação chegar ao seu preço.");
            case FALHA -> flash.addFlashAttribute("aviso",
                    "A Bolsa simulada está fora do ar. Sua ordem foi guardada e será reenviada na recuperação.");
            case REJEITADA -> flash.addFlashAttribute("erro", "Ordem recusada: " + ordem.getMotivo());
            default -> flash.addFlashAttribute("info", "Ordem registrada.");
        }
    }
}
