package br.mackenzie.sentineltrade.integracao;

/* A Bolsa simulada está fora do ar (RNF-04: a ordem vira FALHA e vai para recuperação) */
public class BolsaIndisponivelException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BolsaIndisponivelException() {
        super("Bolsa simulada indisponível.");
    }
}
