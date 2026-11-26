package br.mackenzie.sentineltrade.model;

/** Linha do log de auditoria: quem, quando, o quê e em qual registro (RF-11). */
public class RegistroAuditoria {

    private final String dataHora;
    private final String usuario;
    private final String acao;
    private final String entidade;
    private final Long entidadeId;
    private final String detalhes;

    public RegistroAuditoria(String dataHora, String usuario, String acao, String entidade,
                             Long entidadeId, String detalhes) {
        this.dataHora = dataHora;
        this.usuario = usuario;
        this.acao = acao;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.detalhes = detalhes;
    }

    public String getDataHora() {
        return dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getAcao() {
        return acao;
    }

    public String getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public String getDetalhes() {
        return detalhes;
    }
}
