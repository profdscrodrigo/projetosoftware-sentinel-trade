package br.mackenzie.sentineltrade.service.regras;

import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.service.Formatador;

/** RN-01: compra exige saldo suficiente para quantidade x preço. */
public class RegraSaldo implements RegraValidacao {

    @Override
    public Optional<String> verificar(Ordem ordem, ContextoValidacao contexto) {
        if (ordem.getTipo() == TipoOrdem.COMPRA
                && ordem.getValorTotal().compareTo(contexto.saldoDisponivel()) > 0) {
            return Optional.of("Saldo insuficiente: a ordem custa " + Formatador.formatarDinheiro(ordem.getValorTotal())
                    + " e o saldo é " + Formatador.formatarDinheiro(contexto.saldoDisponivel()) + ".");
        }
        return Optional.empty();
    }
}
