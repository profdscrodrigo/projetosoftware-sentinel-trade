package br.mackenzie.sentineltrade.service.regras;

import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;

/** RN-10: só ativos marcados como negociáveis podem receber ordens. */
public class RegraAtivoNegociavel implements RegraValidacao {

    @Override
    public Optional<String> verificar(Ordem ordem, ContextoValidacao contexto) {
        if (!contexto.ativoNegociavel()) {
            return Optional.of("O ativo " + ordem.getCodigoAtivo() + " está suspenso para negociação.");
        }
        return Optional.empty();
    }
}
