package br.orion.sentineltrade.dominio;

/**
 * Hierarquia de exceções do SentinelTrade.
 * As mensagens são SEGURAS para exibição ao usuário (não vazam detalhes internos);
 * detalhes técnicos vão apenas para o log.
 */
public final class Excecoes {
    private Excecoes() {}

    public abstract static class SentinelException extends RuntimeException {
        protected SentinelException(String mensagem) { super(mensagem); }
        protected SentinelException(String mensagem, Throwable causa) { super(mensagem, causa); }
    }

    /** Entrada fora do formato/intervalo permitido (allowlist). */
    public static final class ValidacaoException extends SentinelException {
        public ValidacaoException(String m) { super(m); }
    }

    /** Violação de regra de negócio, com código estável (usado em auditoria e notificações). */
    public static final class RegraNegocioException extends SentinelException {
        private final CodigoRegra codigo;
        public RegraNegocioException(CodigoRegra codigo) { super(codigo.descricao()); this.codigo = codigo; }
        public RegraNegocioException(CodigoRegra codigo, String detalhe) {
            super(codigo.descricao() + " (" + detalhe + ")");
            this.codigo = codigo;
        }
        public CodigoRegra codigo() { return codigo; }
    }

    /** Ordem de alto valor sem a confirmação adicional (fluxo «extend»). */
    public static final class ConfirmacaoNecessariaException extends SentinelException {
        public ConfirmacaoNecessariaException(String m) { super(m); }
    }

    public static final class AutenticacaoException extends SentinelException {
        public AutenticacaoException(String m) { super(m); }
    }

    public static final class AcessoNegadoException extends SentinelException {
        public AcessoNegadoException(String m) { super(m); }
    }

    public static final class NaoEncontradoException extends SentinelException {
        public NaoEncontradoException(String m) { super(m); }
    }

    /** Conflito lógico: e-mail repetido, chave de idempotência reutilizada com outro conteúdo etc. */
    public static final class ConflitoException extends SentinelException {
        public ConflitoException(String m) { super(m); }
    }

    public static final class LimiteTaxaException extends SentinelException {
        public LimiteTaxaException(String m) { super(m); }
    }

    /** Falha em sistema externo (corretora, provedor de cotações). */
    public static class IntegracaoException extends SentinelException {
        public IntegracaoException(String m) { super(m); }
        public IntegracaoException(String m, Throwable c) { super(m, c); }
    }

    public static final class IntegracaoIndisponivelException extends IntegracaoException {
        public IntegracaoIndisponivelException(String m) { super(m); }
        public IntegracaoIndisponivelException(String m, Throwable c) { super(m, c); }
    }

    public static final class TimeoutIntegracaoException extends IntegracaoException {
        public TimeoutIntegracaoException(String m) { super(m); }
    }

    /** Dado recebido de sistema externo que não passou na revalidação (dado não confiável). */
    public static final class RespostaInvalidaException extends IntegracaoException {
        public RespostaInvalidaException(String m) { super(m); }
    }

    /** Concorrência otimista: outro processo alterou o registro. A operação pode ser repetida. */
    public static final class ConcorrenciaException extends SentinelException {
        public ConcorrenciaException(String m) { super(m); }
    }

    /** Violação de unicidade (ex.: chave de idempotência). */
    public static final class ChaveDuplicadaException extends SentinelException {
        public ChaveDuplicadaException(String m) { super(m); }
    }

    /** Erro de persistência: a mensagem ao usuário é genérica; a causa fica só no log. */
    public static final class PersistenciaException extends SentinelException {
        public PersistenciaException(String m, Throwable c) { super(m, c); }
    }
}
