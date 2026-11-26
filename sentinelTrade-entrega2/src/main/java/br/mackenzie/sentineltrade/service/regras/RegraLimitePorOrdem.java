package br.mackenzie.sentineltrade.service.regras;

import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.service.Formatador;

/** RN-09: o valor de uma ordem não pode passar do limite por ordem da conta. */
public class RegraLimitePorOrdem implements RegraValidacao {

    @Override
    public Optional<String> verificar(Ordem ordem, ContextoValidacao contexto) {
        if (ordem.getValorTotal().compareTo(contexto.limite().getPorOrdem()) > 0) {
            return Optional.of("Valor acima do limite por ordem ("
                    + Formatador.formatarDinheiro(contexto.limite().getPorOrdem()) + ").");
        }
        return Optional.empty();
    }
}
