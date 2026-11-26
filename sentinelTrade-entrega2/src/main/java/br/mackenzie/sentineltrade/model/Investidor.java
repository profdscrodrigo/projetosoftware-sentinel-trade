package br.mackenzie.sentineltrade.model;

/* Investidor: consulta carteira e cotações e envia ou cancela ordens. */
public class Investidor extends Usuario {

    private static final long serialVersionUID = 1L;

    private final long investidorId;
    private final long contaId;

    public Investidor(long id, String nome, String email, long investidorId, long contaId) {
        super(id, nome, email);
        this.investidorId = investidorId;
        this.contaId = contaId;
    }

    public long getInvestidorId() {
        return investidorId;
    }

    public long getContaId() {
        return contaId;
    }

    @Override
    public Perfil getPerfil() {
        return Perfil.INVESTIDOR;
    }

    @Override
    public String getPaginaInicial() {
        return "/investidor";
    }
}
