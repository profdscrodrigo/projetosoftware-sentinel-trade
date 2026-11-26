package br.mackenzie.sentineltrade.service;

/** A mesma ordem foi enviada duas vezes (RF-14 / RN-03). */
public class OrdemDuplicadaException extends RegraNegocioException {

    private static final long serialVersionUID = 1L;

    public OrdemDuplicadaException() {
        super("Esta ordem já foi registrada. O envio repetido foi bloqueado para evitar duplicidade.");
    }
}
