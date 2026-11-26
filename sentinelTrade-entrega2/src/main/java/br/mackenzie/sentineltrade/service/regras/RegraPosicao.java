package br.mackenzie.sentineltrade.service.regras;

import java.util.Optional;

import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.TipoOrdem;

/** RN-02: venda exige quantidade disponível do ativo na carteira. */
public class RegraPosicao implements RegraValidacao {

    @Override
    public Optional<String> verificar(Ordem ordem, ContextoValidacao contexto) {
        if (ordem.getTipo() == TipoOrdem.VENDA && ordem.getQuantidade() > contexto.quantidadeNaCarteira()) {
            return Optional.of("Quantidade maior que a disponível na carteira (você tem "
                    + contexto.quantidadeNaCarteira() + " " + ordem.getCodigoAtivo() + ").");
        }
        return Optional.empty();
    }
}
