package br.mackenzie.sentineltrade.service;

import java.math.BigDecimal;

/** Ordem acima do limite de alto risco: o investidor precisa confirmar (RN-12 / UC19). */
public class ConfirmacaoAltoRiscoException extends RegraNegocioException {

    private static final long serialVersionUID = 1L;

    public ConfirmacaoAltoRiscoException(BigDecimal limiteAltoRisco) {
        super("Esta ordem passa do seu limite de alto risco (" + Formatador.formatarDinheiro(limiteAltoRisco)
                + "). Marque a confirmação para continuar.");
    }
}
