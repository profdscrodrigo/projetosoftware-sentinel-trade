package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.GerenciadorTransacao;
import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.NaoEncontradoException;
import java.util.List;
import java.util.UUID;

/** Funções do Analista de Risco (e do Administrador): limites e acompanhamento de ordens rejeitadas. */
public final class RiscoService {
    private final AutenticacaoService auth;
    private final GerenciadorTransacao tx;
    private final LimiteRiscoRepositorio limites;
    private final UsuarioRepositorio usuarios;
    private final OrdemRepositorio ordens;
    private final AuditoriaService auditoria;

    public RiscoService(AutenticacaoService auth, GerenciadorTransacao tx, LimiteRiscoRepositorio limites,
                        UsuarioRepositorio usuarios, OrdemRepositorio ordens, AuditoriaService auditoria) {
        this.auth = auth; this.tx = tx; this.limites = limites; this.usuarios = usuarios; this.ordens = ordens; this.auditoria = auditoria;
    }

    public LimiteRisco consultarLimites(String token, String idInvestidor) {
        auth.exigir(token, Papel.ANALISTA_RISCO, Papel.ADMINISTRADOR);
        return limites.buscar(Validacoes.uuid(idInvestidor)).orElseThrow(() -> new NaoEncontradoException("Limites não encontrados"));
    }

    public void definirLimites(String token, String idInvestidor, String maxPorOrdem, String limiteDiario, String valorConfirmacao) {
        Sessao s = auth.exigir(token, Papel.ANALISTA_RISCO, Papel.ADMINISTRADOR);
        UUID inv = Validacoes.uuid(idInvestidor);
        LimiteRisco novo = new LimiteRisco(inv, Validacoes.preco(maxPorOrdem), Validacoes.preco(limiteDiario),
                Validacoes.preco(valorConfirmacao), limites.buscar(inv).map(LimiteRisco::versao).orElse(0L));
        tx.executarSemRetorno(() -> {
            usuarios.buscarPorId(inv).filter(u -> u.papel() == Papel.INVESTIDOR).orElseThrow(() -> new NaoEncontradoException("Investidor não encontrado"));
            limites.salvar(novo);
            auditoria.registrar(TipoEvento.LIMITE_ALTERADO, s.usuarioId(), "LIMITE", inv.toString(),
                    "por ordem " + novo.valorMaximoPorOrdem() + "; diário " + novo.limiteDiario() + "; confirmação " + novo.valorConfirmacao());
        });
    }

    public List<Ordem> ordensRejeitadas(String token) {
        auth.exigir(token, Papel.ANALISTA_RISCO, Papel.ADMINISTRADOR);
        return ordens.listarPorStatus(StatusOrdem.REJEITADA, 100);
    }

    public List<Usuario> listarInvestidores(String token) {
        auth.exigir(token, Papel.ANALISTA_RISCO, Papel.ADMINISTRADOR);
        return usuarios.listarPorPapel(Papel.INVESTIDOR);
    }
}
