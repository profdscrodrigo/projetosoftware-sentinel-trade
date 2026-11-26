package br.mackenzie.sentineltrade.service;

/** Erro de regra de negócio. A mensagem é amigável e pode ser mostrada ao usuário. */
public class RegraNegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
