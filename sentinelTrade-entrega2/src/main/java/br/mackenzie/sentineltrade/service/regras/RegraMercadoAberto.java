package br.mackenzie.sentineltrade.service.regras;

import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;

/** RN-10: só é possível negociar com o mercado aberto. */
public class RegraMercadoAberto implements RegraValidacao {

    @Override
    public Optional<String> verificar(Ordem ordem, ContextoValidacao contexto) {
        if (!contexto.mercadoAberto()) {
            return Optional.of("Mercado fechado no momento.");
        }
        return Optional.empty();
    }
}
