package br.mackenzie.sentineltrade.integracao;

import br.mackenzie.sentineltrade.model.Ordem;

/* interface da integração externa com a Bolsa/Corretora (RF-09) */
public interface BolsaGateway {

    /**
     * Envia (ou reenvia, com o mesmo identificador) uma ordem para a Bolsa.
     * @throws BolsaIndisponivelException se a Bolsa estiver fora do ar
     */
    RespostaBolsa enviar(Ordem ordem);
}
