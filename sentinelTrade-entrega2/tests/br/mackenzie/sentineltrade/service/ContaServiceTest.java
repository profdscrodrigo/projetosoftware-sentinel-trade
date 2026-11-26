package br.mackenzie.sentineltrade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.integracao.BolsaSimulada;
import br.mackenzie.sentineltrade.model.AnalistaRisco;
import br.mackenzie.sentineltrade.model.Investidor;
import br.mackenzie.sentineltrade.model.LimiteRisco;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.Posicao;
import br.mackenzie.sentineltrade.model.TipoOrdem;
import br.mackenzie.sentineltrade.notificacao.NotificacaoService;
import br.mackenzie.sentineltrade.repository.AtivoRepository;
import br.mackenzie.sentineltrade.repository.AuditoriaRepository;
import br.mackenzie.sentineltrade.repository.BancoDeDadosInicializador;
import br.mackenzie.sentineltrade.repository.CarteiraRepository;
import br.mackenzie.sentineltrade.repository.ContaRepository;
import br.mackenzie.sentineltrade.repository.NotificacaoRepository;
import br.mackenzie.sentineltrade.repository.OrdemRepository;
import br.mackenzie.sentineltrade.repository.ParametroRepository;
import br.mackenzie.sentineltrade.service.regras.ValidadorOrdem;

/**
 * Testa as consultas do investidor (saldo, carteira, histórico e avisos) e a alteração de limites pelo analista de risco.
 * Usa um banco SQLite de verdade, criado do schema.sql + seed.sql.
 */
class ContaServiceTest {

    private static final Investidor INVESTIDOR_A = new Investidor(3, "Investidor Exemplo A", "a@x", 1, 1);
    private static final AnalistaRisco ANALISTA = new AnalistaRisco(2, "Analista Exemplo", "risco@x");

    @TempDir
    Path pasta;

    private ContaService contaService;
    private OrdemService ordens;
    private NotificacaoService notificacoes;

    @BeforeEach
    void criarBanco() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + pasta.resolve("teste.db"));
        dataSource.setEnforceForeignKeys(true);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        new BancoDeDadosInicializador(jdbc, "database").afterPropertiesSet();

        ContaRepository contas = new ContaRepository(jdbc);
        CarteiraRepository carteira = new CarteiraRepository(jdbc);
        ParametroRepository parametros = new ParametroRepository(jdbc);
        AtivoRepository ativos = new AtivoRepository(jdbc);
        AuditoriaService auditoria = new AuditoriaService(new AuditoriaRepository(jdbc));
        contaService = new ContaService(contas, carteira, auditoria);
        notificacoes = new NotificacaoService(new NotificacaoRepository(jdbc));
        ordens = new OrdemService(new OrdemRepository(jdbc), ativos, contas, carteira, parametros,
                new BolsaSimulada(parametros, ativos), new ValidadorOrdem(), auditoria, notificacoes);
    }

    @Test
    @DisplayName("RF-15: o investidor vê o próprio saldo e a própria carteira")
    void saldoECarteira() {
        assertEquals(new BigDecimal("10000.00"), contaService.saldo(1));

        List<Posicao> posicoes = contaService.carteira(1);
        assertTrue(posicoes.stream().anyMatch(p -> p.getCodigoAtivo().equals("ORCP3") && p.getQuantidade() == 100));
    }

    @Test
    @DisplayName("RF-16 / RNF-09: o histórico mostra só as ordens da própria conta")
    void historicoSoDaPropriaConta() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, new PedidoOrdem(UUID.randomUUID().toString(), "ORCP3",
                TipoOrdem.COMPRA, 10, new BigDecimal("25.40"), false));

        assertTrue(ordens.listarDaConta(1, Optional.empty()).stream().anyMatch(o -> o.getId().equals(ordem.getId())));
        assertTrue(ordens.listarDaConta(2, Optional.empty()).stream().noneMatch(o -> o.getId().equals(ordem.getId())));
    }

    @Test
    @DisplayName("RF-05 / RF-18: o analista de risco altera os limites e eles ficam salvos")
    void alterarLimites() {
        contaService.alterarLimites(ANALISTA, 1,
                new LimiteRisco(new BigDecimal("4000.00"), new BigDecimal("15000.00"), new BigDecimal("2000.00")));

        LimiteRisco salvo = contaService.limite(1);
        assertEquals(0, salvo.getPorOrdem().compareTo(new BigDecimal("4000")));
        assertEquals(0, salvo.getDiario().compareTo(new BigDecimal("15000")));
        assertEquals(0, salvo.getAltoRisco().compareTo(new BigDecimal("2000")));
    }

    @Test
    @DisplayName("RF-05: limites sem sentido são recusados (por ordem maior que o diário)")
    void limitesIncoerentes() {
        LimiteRisco errado = new LimiteRisco(new BigDecimal("9000.00"), new BigDecimal("1000.00"), new BigDecimal("500.00"));

        assertThrows(RegraNegocioException.class, () -> contaService.alterarLimites(ANALISTA, 1, errado));
    }

    @Test
    @DisplayName("RF-12: o investidor recebe um aviso quando a ordem é executada")
    void avisoDeExecucao() {
        ordens.criar(INVESTIDOR_A, new PedidoOrdem(UUID.randomUUID().toString(), "ORCP3",
                TipoOrdem.COMPRA, 7, new BigDecimal("25.40"), false));

        String ultimoAviso = notificacoes.recentes(INVESTIDOR_A.getInvestidorId()).get(0).getMensagem();
        assertTrue(ultimoAviso.contains("7 ORCP3") && ultimoAviso.contains("executada"));
    }
}
