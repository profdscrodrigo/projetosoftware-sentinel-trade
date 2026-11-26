package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.dominio.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** O provedor de cotações é externo e não confiável: tudo que ele devolve é revalidado. */
class CotacaoServiceTest {
    private final Cenario c = new Cenario();
    private final Ticker petr = Ticker.de("PETR4");

    private void injetar(String preco, String fech, java.time.Instant instante, Ticker t) {
        c.app.cotacoesSimuladas.injetarBruta(new Cotacao(t, Dinheiro.de(preco), Dinheiro.de(fech), instante));
    }

    @Test void consultaMostraPrecoHorarioEVariacao() {                   // RF04
        String t = c.ana();
        var q = c.app.cotacoes.consultar(t, "petr4");
        assertEquals(Dinheiro.de("38.50"), q.cotacao().preco());
        assertEquals(c.relogio.instant(), q.cotacao().instante());
        assertEquals(new java.math.BigDecimal("1.05"), q.cotacao().variacaoPercentual());   // (38,50−38,10)/38,10
        assertFalse(q.desatualizada());
        assertEquals(5, c.app.cotacoes.listar(t).size());
    }

    @Test void dadosCorrompidosDoProvedorSaoDescartados() {
        String t = c.ana();
        injetar("-5.00", "38.10", c.relogio.instant(), petr);
        assertThrows(Excecoes.NaoEncontradoException.class, () -> c.app.cotacoes.consultar(t, "PETR4"));     // preço negativo
        injetar("38.50", "0.00", c.relogio.instant(), petr);
        assertThrows(Excecoes.NaoEncontradoException.class, () -> c.app.cotacoes.consultar(t, "PETR4"));     // fechamento zero
        injetar("38.50", "38.10", c.relogio.instant().plusSeconds(3600), petr);
        assertThrows(Excecoes.NaoEncontradoException.class, () -> c.app.cotacoes.consultar(t, "PETR4"));     // data no futuro
        injetar("90.00", "38.10", c.relogio.instant(), petr);
        assertThrows(Excecoes.NaoEncontradoException.class, () -> c.app.cotacoes.consultar(t, "PETR4"));     // +136%
        injetar("38.50", "38.10", c.relogio.instant(), Ticker.de("VALE3"));                                  // ativo trocado
        assertThrows(Excecoes.NaoEncontradoException.class, () -> c.app.cotacoes.consultar(t, "PETR4"));
    }

    @Test void dadoCorrompidoNuncaSubstituiAUltimaCotacaoValida() {
        String t = c.ana();
        c.app.cotacoes.consultar(t, "PETR4");                                // cache válido
        injetar("-5.00", "38.10", c.relogio.instant(), petr);
        assertEquals(Dinheiro.de("38.50"), c.app.cotacoes.consultar(t, "PETR4").cotacao().preco());
    }

    @Test void provedorForaDoArDevolveUltimaCotacaoMarcadaComoDesatualizadaComOTempo() {
        String t = c.ana();
        c.app.cotacoes.consultar(t, "PETR4");
        c.app.cotacoesSimuladas.setIndisponivel(true);
        assertFalse(c.app.cotacoes.consultar(t, "PETR4").desatualizada());
        c.relogio.avancar(Duration.ofMinutes(5));
        c.app.auth.exigir(c.ana());                                          // nova sessão (a anterior expirou? não: 5 min < 15)
        assertTrue(c.app.cotacoes.consultar(t, "PETR4").desatualizada());
    }

    @Test void consultaExigeAutenticacaoEAtivoValido() {
        assertThrows(Excecoes.AutenticacaoException.class, () -> c.app.cotacoes.consultar("sem-token", "PETR4"));
        String t = c.ana();
        assertThrows(Excecoes.ValidacaoException.class, () -> c.app.cotacoes.consultar(t, "PETR4'; --"));
        assertThrows(Excecoes.NaoEncontradoException.class, () -> c.app.cotacoes.consultar(t, "ZZZZ9"));
    }
}
