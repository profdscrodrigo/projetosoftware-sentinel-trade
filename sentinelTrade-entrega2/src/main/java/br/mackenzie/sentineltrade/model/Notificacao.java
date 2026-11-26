package br.mackenzie.sentineltrade.model;

/* Aviso enviado ao investidor (RF-12). */
public class Notificacao {

    private final String mensagem;
    private final String dataHora;

    public Notificacao(String mensagem, String dataHora) {
        this.mensagem = mensagem;
        this.dataHora = dataHora;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getDataHora() {
        return dataHora;
    }
}
