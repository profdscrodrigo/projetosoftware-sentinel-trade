package br.mackenzie.sentineltrade.service.regras;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.mackenzie.sentineltrade.model.Ordem;

/**
 * Aplica todas as regras, em ordem. Para na primeira regra que recusar a ordem.
 * Para criar uma regra nova basta implementar RegraValidacao e incluir na lista.
 */
@Component
public class ValidadorOrdem {

    private final List<RegraValidacao> regras = List.of(
            new RegraMercadoAberto(),
            new RegraAtivoNegociavel(),
            new RegraSaldo(),
            new RegraPosicao(),
            new RegraLimitePorOrdem(),
            new RegraLimiteDiario());

    public Optional<String> validar(Ordem ordem, ContextoValidacao contexto) {
        for (RegraValidacao regra : regras) {
            Optional<String> motivo = regra.verificar(ordem, contexto);
            if (motivo.isPresent()) {
                return motivo;
            }
        }
        return Optional.empty();
    }
}
