package br.mackenzie.sentineltrade.integracao;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.repository.AtivoRepository;
import br.mackenzie.sentineltrade.repository.ParametroRepository;

/* Regra: compra executa se o preço da ordem >= cotação; venda executa se o preço <= cotação. Se não der o preço, a ordem fica PENDENTE até a cotação mudar */
@Component
public class BolsaSimulada implements BolsaGateway {

    private final ParametroRepository parametros;
    private final AtivoRepository ativos;

    public BolsaSimulada(ParametroRepository parametros, AtivoRepository ativos) {
        this.parametros = parametros;
        this.ativos = ativos;
    }

    @Override
    public RespostaBolsa enviar(Ordem ordem) {
        if (!parametros.isVerdadeiro("BOLSA_DISPONIVEL")) {
            throw new BolsaIndisponivelException();
        }
        BigDecimal cotacao = ativos.buscarPorId(ordem.getAtivoId())
                .map(a -> a.getCotacao())
                .orElse(BigDecimal.ZERO);

        boolean executa = ordem.getTipo() == TipoOrdem.COMPRA
                ? ordem.getPreco().compareTo(cotacao) >= 0
                : ordem.getPreco().compareTo(cotacao) <= 0;

        return executa
                ? new RespostaBolsa("EXECUTADA", "Ordem executada pela Bolsa simulada.")
                : new RespostaBolsa("PENDENTE", "Aguardando a cotação atingir o preço da ordem.");
    }
}
