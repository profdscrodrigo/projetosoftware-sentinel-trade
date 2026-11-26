package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.Repositorios.ContaRepositorio;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.PosicaoRepositorio;
import br.orion.sentineltrade.dominio.*;
import java.util.ArrayList;
import java.util.List;

/** Consulta de saldo e carteira. O investidor é sempre derivado da SESSÃO (nunca de parâmetro): sem acesso a dados de terceiros. */
public final class CarteiraService {
    public record SaldoConta(Dinheiro disponivel, Dinheiro bloqueado) {}
    public record ItemCarteira(Ticker ticker, int quantidade, int quantidadeBloqueada, Dinheiro precoMedio,
                               Dinheiro cotacaoAtual, boolean cotacaoDesatualizada, Dinheiro valorEstimado) {}
    public record ResumoCarteira(SaldoConta saldo, List<ItemCarteira> itens, Dinheiro patrimonioEstimado) {}

    private final AutenticacaoService auth;
    private final ContaRepositorio contas;
    private final PosicaoRepositorio posicoes;
    private final CotacaoService cotacoes;

    public CarteiraService(AutenticacaoService auth, ContaRepositorio contas, PosicaoRepositorio posicoes, CotacaoService cotacoes) {
        this.auth = auth; this.contas = contas; this.posicoes = posicoes; this.cotacoes = cotacoes;
    }

    public SaldoConta consultarSaldo(String token) {
        Sessao s = auth.exigir(token, Papel.INVESTIDOR);
        Conta c = contas.buscarPorInvestidor(s.usuarioId()).orElseThrow(() -> new IllegalStateException("Conta inexistente"));
        return new SaldoConta(c.saldoDisponivel(), c.saldoBloqueado());
    }

    public ResumoCarteira consultarCarteira(String token) {
        SaldoConta saldo = consultarSaldo(token);
        Sessao s = auth.exigir(token, Papel.INVESTIDOR);
        List<ItemCarteira> itens = new ArrayList<>();
        Dinheiro patrimonio = saldo.disponivel().mais(saldo.bloqueado());
        for (Posicao p : posicoes.listarPorInvestidor(s.usuarioId())) {
            if (p.quantidade() == 0) continue;
            var cot = cotacoes.obter(p.ticker());
            Dinheiro preco = cot.map(c -> c.cotacao().preco()).orElse(null);
            Dinheiro valor = preco != null ? preco.vezes(p.quantidade()) : p.custoTotal();   // sem cotação: valoriza pelo custo
            itens.add(new ItemCarteira(p.ticker(), p.quantidade(), p.quantidadeBloqueada(), p.precoMedio(), preco,
                    cot.map(CotacaoService.CotacaoConsultada::desatualizada).orElse(true), valor));
            patrimonio = patrimonio.mais(valor);
        }
        return new ResumoCarteira(saldo, itens, patrimonio);
    }
}
