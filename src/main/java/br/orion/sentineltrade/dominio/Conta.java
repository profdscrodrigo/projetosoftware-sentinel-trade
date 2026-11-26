package br.orion.sentineltrade.dominio;

import br.orion.sentineltrade.dominio.Excecoes.RegraNegocioException;
import br.orion.sentineltrade.dominio.Excecoes.ValidacaoException;
import java.util.UUID;

/**
 * Conta financeira simulada. Separa saldo DISPONÍVEL de saldo BLOQUEADO (reservado por ordens em aberto),
 * o que impede gastar duas vezes o mesmo dinheiro em ordens concorrentes.
 */
public final class Conta {
    private final UUID id;
    private final UUID investidorId;
    private Dinheiro saldoDisponivel;
    private Dinheiro saldoBloqueado;
    private long versao;

    public Conta(UUID id, UUID investidorId, Dinheiro disponivel, Dinheiro bloqueado, long versao) {
        this.id = id; this.investidorId = investidorId;
        this.saldoDisponivel = disponivel; this.saldoBloqueado = bloqueado; this.versao = versao;
    }

    public static Conta nova(UUID investidorId) {
        return new Conta(UUID.randomUUID(), investidorId, Dinheiro.ZERO, Dinheiro.ZERO, 0);
    }

    public void depositar(Dinheiro v) {
        if (!v.positivo()) throw new ValidacaoException("Valor de depósito deve ser positivo");
        saldoDisponivel = saldoDisponivel.mais(v);
    }

    /** Reserva saldo para uma compra. Tudo ou nada. */
    public void bloquear(Dinheiro v) {
        if (saldoDisponivel.compareTo(v) < 0) throw new RegraNegocioException(CodigoRegra.SALDO_INSUFICIENTE);
        saldoDisponivel = saldoDisponivel.menos(v);
        saldoBloqueado = saldoBloqueado.mais(v);
    }

    /** Devolve ao disponível uma reserva não utilizada (cancelamento/rejeição). */
    public void liberar(Dinheiro v) {
        exigirBloqueado(v);
        saldoBloqueado = saldoBloqueado.menos(v);
        saldoDisponivel = saldoDisponivel.mais(v);
    }

    /** Consome reserva numa execução: gasta {@code gasto} e devolve a diferença (preço melhor que o limite). */
    public void consumirReserva(Dinheiro reservado, Dinheiro gasto) {
        exigirBloqueado(reservado);
        if (gasto.compareTo(reservado) > 0) throw new IllegalStateException("Execução acima do valor reservado");
        saldoBloqueado = saldoBloqueado.menos(reservado);
        saldoDisponivel = saldoDisponivel.mais(reservado.menos(gasto));
    }

    public void creditar(Dinheiro v) { saldoDisponivel = saldoDisponivel.mais(v); }

    private void exigirBloqueado(Dinheiro v) {
        if (saldoBloqueado.compareTo(v) < 0) throw new IllegalStateException("Inconsistência: saldo bloqueado menor que a reserva");
    }

    public UUID id() { return id; }
    public UUID investidorId() { return investidorId; }
    public Dinheiro saldoDisponivel() { return saldoDisponivel; }
    public Dinheiro saldoBloqueado() { return saldoBloqueado; }
    public long versao() { return versao; }
    /** Uso exclusivo da camada de persistência. */
    public void versaoPersistida(long v) { this.versao = v; }
}
