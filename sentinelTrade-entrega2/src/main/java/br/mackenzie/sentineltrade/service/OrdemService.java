package br.mackenzie.sentineltrade.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.integracao.BolsaGateway;
import br.mackenzie.sentineltrade.integracao.BolsaIndisponivelException;
import br.mackenzie.sentineltrade.integracao.RespostaBolsa;
import br.mackenzie.sentineltrade.model.Ativo;
import br.mackenzie.sentineltrade.model.HistoricoStatus;
import br.mackenzie.sentineltrade.model.Investidor;
import br.mackenzie.sentineltrade.model.LimiteRisco;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.OrdemRejeitada;
import br.mackenzie.sentineltrade.model.StatusOrdem;
import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.model.Usuario;
import br.mackenzie.sentineltrade.notificacao.NotificacaoService;
import br.mackenzie.sentineltrade.repository.AtivoRepository;
import br.mackenzie.sentineltrade.repository.CarteiraRepository;
import br.mackenzie.sentineltrade.repository.ContaRepository;
import br.mackenzie.sentineltrade.repository.OrdemRepository;
import br.mackenzie.sentineltrade.repository.ParametroRepository;
import br.mackenzie.sentineltrade.service.regras.ContextoValidacao;
import br.mackenzie.sentineltrade.service.regras.ValidadorOrdem;

/**
 * Regras do ciclo de vida da ordem: criar, consultar, atualizar o preço e cancelar (RF-07 a RF-10, RF-13, RF-14).
 * Fluxo: CRIADA -> (validação) -> VALIDADA -> (Bolsa) -> ENVIADA -> EXECUTADA.
 */
@Service
public class OrdemService {

    private final OrdemRepository ordens;
    private final AtivoRepository ativos;
    private final ContaRepository contas;
    private final CarteiraRepository carteira;
    private final ParametroRepository parametros;
    private final BolsaGateway bolsa;
    private final ValidadorOrdem validador;
    private final AuditoriaService auditoria;
    private final NotificacaoService notificacoes;

    public OrdemService(OrdemRepository ordens, AtivoRepository ativos, ContaRepository contas,
                        CarteiraRepository carteira, ParametroRepository parametros, BolsaGateway bolsa,
                        ValidadorOrdem validador, AuditoriaService auditoria, NotificacaoService notificacoes) {
        this.ordens = ordens;
        this.ativos = ativos;
        this.contas = contas;
        this.carteira = carteira;
        this.parametros = parametros;
        this.bolsa = bolsa;
        this.validador = validador;
        this.auditoria = auditoria;
        this.notificacoes = notificacoes;
    }

    // ------------------------------------------------------------------ CRIAR

    @Transactional(noRollbackFor = OrdemDuplicadaException.class)
    public Ordem criar(Investidor investidor, PedidoOrdem pedido) {
        // 1) Prevenção de duplicidade (RF-14 / RN-03)
        if (ordens.existeChave(pedido.chaveIdempotencia())) {
            auditoria.registrar(investidor.getId(), "ORDEM_DUPLICADA_BLOQUEADA", "ordem", null,
                    "Envio repetido bloqueado");
            throw new OrdemDuplicadaException();
        }

        // 2) O ativo precisa existir (allowlist: só aceita códigos cadastrados)
        Ativo ativo = ativos.buscarPorCodigo(pedido.codigoAtivo())
                .orElseThrow(() -> new RegraNegocioException("Ativo não encontrado."));

        Ordem ordem = new Ordem(pedido.chaveIdempotencia(), investidor.getContaId(), ativo.getId(),
                ativo.getCodigo(), pedido.tipo(), pedido.quantidade(), pedido.preco());

        // 3) Operação de alto risco precisa de confirmação extra (RN-12 / «extend» UC19)
        LimiteRisco limite = contas.buscarLimite(investidor.getContaId());
        if (ordem.getValorTotal().compareTo(limite.getAltoRisco()) > 0 && !pedido.confirmaAltoRisco()) {
            throw new ConfirmacaoAltoRiscoException(limite.getAltoRisco());
        }

        ordens.inserir(ordem);
        auditoria.registrar(investidor.getId(), "ORDEM_SOLICITADA", "ordem", ordem.getId(),
                ordem.getTipo() + " " + ordem.getQuantidade() + " " + ordem.getCodigoAtivo());

        // 4) Validação das regras de negócio (RF-08)
        Optional<String> motivo = validador.validar(ordem, montarContexto(ordem, ativo, limite));
        if (motivo.isPresent()) {
            mudarStatus(ordem, StatusOrdem.REJEITADA, motivo.get());
            return ordem;
        }
        mudarStatus(ordem, StatusOrdem.VALIDADA, null);

        // 5) Envio para a Bolsa simulada (RF-09)
        enviarParaBolsa(ordem);
        return ordem;
    }

    // ------------------------------------------------------------------ CONSULTAR

    public List<Ordem> listarDaConta(long contaId, Optional<StatusOrdem> filtro) {
        return filtro.map(status -> ordens.listarPorContaEStatus(contaId, status))
                .orElseGet(() -> ordens.listarPorConta(contaId));
    }

    public Map<Long, List<HistoricoStatus>> historicoDaConta(long contaId) {
        return ordens.historicoDaConta(contaId);
    }

    public List<Ordem> listarComFalha() {
        return ordens.listarPorStatus(StatusOrdem.FALHA);
    }

    public List<OrdemRejeitada> listarRejeitadas() {
        return ordens.listarRejeitadas(50);
    }

    // ------------------------------------------------------------------ ATUALIZAR

    /** Altera o preço de uma ordem que ainda aguarda execução e reenvia para a Bolsa. */
    @Transactional
    public Ordem alterarPreco(Investidor investidor, long ordemId, BigDecimal novoPreco) {
        Ordem ordem = buscarDoInvestidor(investidor, ordemId);
        if (!ordem.isPrecoAlteravel()) {
            throw new RegraNegocioException("Só é possível alterar o preço de ordens aguardando execução.");
        }
        BigDecimal precoAntigo = ordem.getPreco();
        ordem.alterarPreco(novoPreco);

        // O novo preço passa de novo pelas regras (saldo e limites)
        Ativo ativo = ativos.buscarPorId(ordem.getAtivoId())
                .orElseThrow(() -> new RegraNegocioException("Ativo não encontrado."));
        Optional<String> motivo = validador.validar(ordem,
                montarContexto(ordem, ativo, contas.buscarLimite(ordem.getContaId())));
        if (motivo.isPresent()) {
            throw new RegraNegocioException("Novo preço recusado: " + motivo.get());
        }

        ordens.atualizarPreco(ordem);
        auditoria.registrar(investidor.getId(), "ORDEM_PRECO_ALTERADO", "ordem", ordem.getId(),
                precoAntigo + " -> " + novoPreco);
        enviarParaBolsa(ordem);
        return ordem;
    }

    /** Chamado quando a cotação muda: tenta executar as ordens que estavam aguardando. */
    @Transactional
    public int processarPendentes(long ativoId) {
        int executadas = 0;
        for (Ordem ordem : ordens.listarPorAtivoEStatus(ativoId, StatusOrdem.ENVIADA)) {
            enviarParaBolsa(ordem);
            if (ordem.getStatus() == StatusOrdem.EXECUTADA) {
                executadas++;
            }
        }
        return executadas;
    }

    /** RF-13: reenvia as ordens com FALHA usando o MESMO identificador (sem duplicar). */
    @Transactional
    public int recuperarFalhas(Usuario quem) {
        auditoria.registrar(quem.getId(), "RECUPERACAO_INICIADA", "ordem", null, null);
        int recuperadas = 0;
        for (Ordem ordem : ordens.listarPorStatus(StatusOrdem.FALHA)) {
            enviarParaBolsa(ordem);
            if (ordem.getStatus() != StatusOrdem.FALHA) {
                recuperadas++;
            }
        }
        return recuperadas;
    }

    // ------------------------------------------------------------------ CANCELAR

    @Transactional
    public void cancelar(Investidor investidor, long ordemId) {
        Ordem ordem = buscarDoInvestidor(investidor, ordemId);
        if (!ordem.isCancelavel()) {
            throw new RegraNegocioException("Esta ordem não pode mais ser cancelada (status: "
                    + ordem.getStatus().getDescricao() + ").");
        }
        mudarStatus(ordem, StatusOrdem.CANCELADA, "Cancelada pelo investidor.");
        auditoria.registrar(investidor.getId(), "ORDEM_CANCELADA_PELO_INVESTIDOR", "ordem", ordem.getId(), null);
    }

    // ------------------------------------------------------------------ AUXILIARES

    /** RN-08: o investidor só enxerga as próprias ordens. Ordem de outro = "não encontrada". */
    private Ordem buscarDoInvestidor(Investidor investidor, long ordemId) {
        return ordens.buscarPorId(ordemId)
                .filter(o -> o.getContaId() == investidor.getContaId())
                .orElseThrow(() -> new RegraNegocioException("Ordem não encontrada."));
    }

    private ContextoValidacao montarContexto(Ordem ordem, Ativo ativo, LimiteRisco limite) {
        return new ContextoValidacao(
                contas.buscarSaldo(ordem.getContaId()),
                carteira.quantidade(ordem.getContaId(), ordem.getAtivoId()),
                limite,
                ordens.totalDoDia(ordem.getContaId(), ordem.getId()),
                ativo.isNegociavel(),
                parametros.isVerdadeiro("MERCADO_ABERTO"));
    }

    private void enviarParaBolsa(Ordem ordem) {
        try {
            RespostaBolsa resposta = bolsa.enviar(ordem);
            if (!resposta.isValida()) {
                registrarFalhaDeEnvio(ordem, "Resposta inválida da Bolsa simulada.");
                return;
            }
            if (ordem.getStatus() != StatusOrdem.ENVIADA) {
                mudarStatus(ordem, StatusOrdem.ENVIADA, null);
            }
            if (resposta.isExecutada()) {
                executar(ordem);
            }
        } catch (BolsaIndisponivelException e) {
            registrarFalhaDeEnvio(ordem, "Bolsa simulada indisponível.");
        }
    }

    /** RN-07: falha na Bolsa nunca mexe na carteira; a ordem fica guardada como FALHA. */
    private void registrarFalhaDeEnvio(Ordem ordem, String motivo) {
        if (ordem.getStatus().podeMudarPara(StatusOrdem.FALHA)) {
            mudarStatus(ordem, StatusOrdem.FALHA, motivo);
        }
    }

    /** Aplica a execução na conta e na carteira (confere saldo/posição de novo). */
    private void executar(Ordem ordem) {
        long contaId = ordem.getContaId();
        BigDecimal total = ordem.getValorTotal();

        if (ordem.getTipo() == TipoOrdem.COMPRA) {
            BigDecimal saldo = contas.buscarSaldo(contaId);
            if (saldo.compareTo(total) < 0) {
                mudarStatus(ordem, StatusOrdem.REJEITADA, "Saldo insuficiente no momento da execução.");
                return;
            }
            contas.atualizarSaldo(contaId, saldo.subtract(total));
            carteira.registrarCompra(contaId, ordem.getAtivoId(), ordem.getQuantidade(), ordem.getPreco());
        } else {
            if (carteira.quantidade(contaId, ordem.getAtivoId()) < ordem.getQuantidade()) {
                mudarStatus(ordem, StatusOrdem.REJEITADA, "Quantidade insuficiente na carteira no momento da execução.");
                return;
            }
            carteira.registrarVenda(contaId, ordem.getAtivoId(), ordem.getQuantidade());
            contas.atualizarSaldo(contaId, contas.buscarSaldo(contaId).add(total));
        }
        mudarStatus(ordem, StatusOrdem.EXECUTADA, null);
    }

    /** Toda mudança de status passa por aqui (o gatilho do banco grava histórico e auditoria - RN-05). */
    private void mudarStatus(Ordem ordem, StatusOrdem novo, String motivo) {
        ordem.mudarStatus(novo, motivo);
        ordens.atualizarStatus(ordem);
        if (novo.notificaInvestidor()) {
            notificacoes.notificarOrdem(ordem);
        }
    }
}
