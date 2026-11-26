package br.mackenzie.sentineltrade.service;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.model.ContaComLimite;
import br.mackenzie.sentineltrade.model.LimiteRisco;
import br.mackenzie.sentineltrade.model.Posicao;
import br.mackenzie.sentineltrade.model.Usuario;
import br.mackenzie.sentineltrade.repository.CarteiraRepository;
import br.mackenzie.sentineltrade.repository.ContaRepository;

/** Saldo, carteira e limites de risco (RF-05, RF-15). */
@Service
public class ContaService {

    private final ContaRepository contas;
    private final CarteiraRepository carteira;
    private final AuditoriaService auditoria;

    public ContaService(ContaRepository contas, CarteiraRepository carteira, AuditoriaService auditoria) {
        this.contas = contas;
        this.carteira = carteira;
        this.auditoria = auditoria;
    }

    public BigDecimal saldo(long contaId) {
        return contas.buscarSaldo(contaId);
    }

    public List<Posicao> carteira(long contaId) {
        return carteira.listarPorConta(contaId);
    }

    public LimiteRisco limite(long contaId) {
        return contas.buscarLimite(contaId);
    }

    public List<ContaComLimite> listarContasComLimites() {
        return contas.listarComLimites();
    }

    @Transactional
    public void alterarLimites(Usuario analista, long contaId, LimiteRisco novo) {
        if (!contas.existe(contaId)) {
            throw new RegraNegocioException("Conta não encontrada.");
        }
        if (novo.getPorOrdem().compareTo(novo.getDiario()) > 0) {
            throw new RegraNegocioException("O limite por ordem não pode ser maior que o limite diário.");
        }
        if (novo.getAltoRisco().compareTo(novo.getPorOrdem()) > 0) {
            throw new RegraNegocioException("O limite de alto risco não pode ser maior que o limite por ordem.");
        }
        contas.atualizarLimite(contaId, novo, analista.getId());
        auditoria.registrar(analista.getId(), "LIMITE_ALTERADO", "limite_risco", contaId,
                "por ordem " + novo.getPorOrdem() + ", diário " + novo.getDiario()
                        + ", alto risco " + novo.getAltoRisco());
    }
}
