package br.mackenzie.sentineltrade.service.regras;

import java.math.BigDecimal;

import br.mackenzie.sentineltrade.model.LimiteRisco;

/**
 * Tudo que as regras precisam saber para validar uma ordem.
 * Assim as regras não acessam o banco e ficam fáceis de testar.
 */
public record ContextoValidacao(BigDecimal saldoDisponivel,
                                int quantidadeNaCarteira,
                                LimiteRisco limite,
                                BigDecimal totalOrdensHoje,
                                boolean ativoNegociavel,
                                boolean mercadoAberto) {
}
