package br.mackenzie.sentineltrade.model;

/* Uma mudança de status de uma ordem (RF-10). */
public class HistoricoStatus {

    private final long ordemId;
    private final String statusNovo;
    private final String motivo;
    private final String dataHora;

    public HistoricoStatus(long ordemId, String statusNovo, String motivo, String dataHora) {
        this.ordemId = ordemId;
        this.statusNovo = statusNovo;
        this.motivo = motivo;
        this.dataHora = dataHora;
    }

    public long getOrdemId() {
        return ordemId;
    }

    public String getStatusNovo() {
        return statusNovo;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getDataHora() {
        return dataHora;
    }
}
