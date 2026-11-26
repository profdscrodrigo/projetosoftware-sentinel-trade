package br.orion.sentineltrade.aplicacao.porta;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Porta de integração com a Bolsa/Corretora simulada. Todo método pode lançar
 * {@code IntegracaoException} (indisponibilidade, timeout, resposta inválida).
 */
public interface CorretoraGateway {
    /** Idempotente por {@code idCliente}: reenvio devolve o mesmo ack sem criar nova ordem. */
    AckCorretora enviar(OrdemParaEnvio ordem);
    /** @return true se a corretora cancelou (ou já havia cancelado); false se a ordem já estava totalmente executada/desconhecida. */
    boolean cancelar(UUID idCliente);
    List<RelatorioExecucao> consultarRelatorios();
    /** Confirma o recebimento; relatórios não confirmados são reentregues. */
    void confirmarRelatorios(Collection<String> idsExecucao);
}
