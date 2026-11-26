package br.orion.sentineltrade.aplicacao.porta;

import br.orion.sentineltrade.dominio.Dinheiro;
import java.time.Instant;
import java.util.UUID;

/** Relatório assíncrono da corretora: execução (parcial/total) ou rejeição tardia. Entrega "pelo menos uma vez". */
public record RelatorioExecucao(String idExecucao, UUID ordemId, Tipo tipo, int quantidade,
                                Dinheiro preco, String motivo, Instant em) {
    public enum Tipo { EXECUCAO, REJEICAO }
}
