package br.mackenzie.sentineltrade.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.model.Ativo;
import br.mackenzie.sentineltrade.model.Usuario;
import br.mackenzie.sentineltrade.repository.AtivoRepository;
import br.mackenzie.sentineltrade.repository.ParametroRepository;

/** Ativos, cotações e parâmetros do mercado simulado (RF-06, RF-17). */
@Service
public class MercadoService {

    /** Únicos parâmetros que podem ser alterados pela tela (allowlist). */
    private static final Set<String> PARAMETROS_EDITAVEIS = Set.of("MERCADO_ABERTO", "BOLSA_DISPONIVEL");

    private final AtivoRepository ativos;
    private final ParametroRepository parametros;
    private final OrdemService ordemService;
    private final AuditoriaService auditoria;

    public MercadoService(AtivoRepository ativos, ParametroRepository parametros, OrdemService ordemService,
                          AuditoriaService auditoria) {
        this.ativos = ativos;
        this.parametros = parametros;
        this.ordemService = ordemService;
        this.auditoria = auditoria;
    }

    public List<Ativo> listarAtivos() {
        return ativos.listar();
    }

    public boolean isMercadoAberto() {
        return parametros.isVerdadeiro("MERCADO_ABERTO");
    }

    public boolean isBolsaDisponivel() {
        return parametros.isVerdadeiro("BOLSA_DISPONIVEL");
    }

    /** Muda a cotação e tenta executar as ordens que aguardavam esse preço. Devolve quantas executou. */
    @Transactional
    public int atualizarCotacao(Usuario admin, long ativoId, BigDecimal novoPreco) {
        Ativo ativo = ativos.buscarPorId(ativoId)
                .orElseThrow(() -> new RegraNegocioException("Ativo não encontrado."));
        BigDecimal anterior = ativo.getCotacao();
        BigDecimal variacao = anterior.signum() == 0 ? BigDecimal.ZERO
                : novoPreco.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior, 2, RoundingMode.HALF_UP);

        ativos.inserirCotacao(ativoId, novoPreco, variacao);
        auditoria.registrar(admin.getId(), "COTACAO_ALTERADA", "ativo", ativoId,
                ativo.getCodigo() + ": " + anterior + " -> " + novoPreco);
        return ordemService.processarPendentes(ativoId);
    }

    @Transactional
    public void alterarParametro(Usuario admin, String chave, String valor) {
        if (!PARAMETROS_EDITAVEIS.contains(chave) || !("true".equals(valor) || "false".equals(valor))) {
            throw new RegraNegocioException("Parâmetro inválido.");
        }
        parametros.atualizar(chave, valor);
        auditoria.registrar(admin.getId(), "PARAMETRO_ALTERADO", "parametro_sistema", null, chave + " = " + valor);
    }
}
