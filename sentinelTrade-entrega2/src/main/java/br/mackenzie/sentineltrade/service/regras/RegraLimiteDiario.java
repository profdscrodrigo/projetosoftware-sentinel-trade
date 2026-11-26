package br.mackenzie.sentineltrade.service.regras;

import java.math.BigDecimal;
import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.service.Formatador;

/** RN-09: a soma das ordens do dia não pode passar do limite diário. */
public class RegraLimiteDiario implements RegraValidacao {

    @Override
    public Optional<String> verificar(Ordem ordem, ContextoValidacao contexto) {
        BigDecimal totalComEstaOrdem = contexto.totalOrdensHoje().add(ordem.getValorTotal());
        if (totalComEstaOrdem.compareTo(contexto.limite().getDiario()) > 0) {
            return Optional.of("Limite diário excedido (" + Formatador.formatarDinheiro(contexto.limite().getDiario())
                    + " por dia).");
        }
        return Optional.empty();
    }
}
