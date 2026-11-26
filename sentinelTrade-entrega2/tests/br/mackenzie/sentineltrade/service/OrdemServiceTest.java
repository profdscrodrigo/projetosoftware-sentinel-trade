package br.mackenzie.sentineltrade.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.integracao.BolsaSimulada;
import br.mackenzie.sentineltrade.model.Administrador;
import br.mackenzie.sentineltrade.model.Investidor;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.model.StatusOrdem;
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
 * Testa o ciclo de vida completo das ordens com um banco SQLite de verdade (criado do schema.sql + seed.sql).
 * Dados do seed: Investidor A tem saldo R$ 10.000, 100 ORCP3 (cotação R$ 25,40) e limites 5.000 / 20.000 / 3.000.
 */
class OrdemServiceTest {

    private static final Investidor INVESTIDOR_A = new Investidor(3, "Investidor Exemplo A", "a@x", 1, 1);
    private static final Investidor INVESTIDOR_B = new Investidor(4, "Investidor Exemplo B", "b@x", 2, 2);
    private static final Administrador ADMIN = new Administrador(1, "Admin Exemplo", "admin@x");

    @TempDir
    Path pasta;

    private JdbcTemplate jdbc;
    private OrdemService ordens;
    private MercadoService mercado;
    private ContaRepository contas;
    private CarteiraRepository carteira;
    private ParametroRepository parametros;

    @BeforeEach
    void criarBanco() throws Exception {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + pasta.resolve("teste.db"));
        dataSource.setEnforceForeignKeys(true);
        jdbc = new JdbcTemplate(dataSource);
        new BancoDeDadosInicializador(jdbc, "database").afterPropertiesSet();

        contas = new ContaRepository(jdbc);
        carteira = new CarteiraRepository(jdbc);
        parametros = new ParametroRepository(jdbc);
        AtivoRepository ativos = new AtivoRepository(jdbc);
        AuditoriaService auditoria = new AuditoriaService(new AuditoriaRepository(jdbc));
        ordens = new OrdemService(new OrdemRepository(jdbc), ativos, contas, carteira, parametros,
                new BolsaSimulada(parametros, ativos), new ValidadorOrdem(), auditoria,
                new NotificacaoService(new NotificacaoRepository(jdbc)));
        mercado = new MercadoService(ativos, parametros, ordens, auditoria);
    }

    private PedidoOrdem pedido(TipoOrdem tipo, String ativo, int quantidade, String preco) {
        return new PedidoOrdem(UUID.randomUUID().toString(), ativo, tipo, quantidade, new BigDecimal(preco), false);
    }

    @Test
    @DisplayName("Compra válida é executada: saldo diminui e carteira aumenta")
    void compraExecutada() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.COMPRA, "ORCP3", 10, "25.40"));

        assertEquals(StatusOrdem.EXECUTADA, ordem.getStatus());
        assertEquals(new BigDecimal("9746.00"), contas.buscarSaldo(1));
        assertEquals(110, carteira.quantidade(1, 1));
    }

    @Test
    @DisplayName("Compra abaixo da cotação fica aguardando e executa quando a cotação cai")
    void compraAguardaCotacao() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.COMPRA, "ORCP3", 10, "20.00"));
        assertEquals(StatusOrdem.ENVIADA, ordem.getStatus());

        int executadas = mercado.atualizarCotacao(ADMIN, 1, new BigDecimal("19.50"));

        assertEquals(1, executadas);
        assertEquals(new BigDecimal("9800.00"), contas.buscarSaldo(1));
    }

    @Test
    @DisplayName("RF-14: a mesma ordem enviada duas vezes é bloqueada")
    void duplicidadeBloqueada() {
        PedidoOrdem pedido = pedido(TipoOrdem.COMPRA, "ORCP3", 1, "25.40");
        ordens.criar(INVESTIDOR_A, pedido);

        assertThrows(OrdemDuplicadaException.class, () -> ordens.criar(INVESTIDOR_A, pedido));
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM ordem WHERE chave_idempotencia = ?",
                Integer.class, pedido.chaveIdempotencia());
        assertEquals(1, total);
    }

    @Test
    @DisplayName("RN-02: venda de ativo que não está na carteira é rejeitada")
    void vendaSemPosicao() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.VENDA, "STNL4", 5, "12.75"));
        assertEquals(StatusOrdem.REJEITADA, ordem.getStatus());
    }

    @Test
    @DisplayName("RN-12: ordem acima do limite de alto risco pede confirmação")
    void altoRiscoPedeConfirmacao() {
        PedidoOrdem semConfirmar = pedido(TipoOrdem.COMPRA, "FICT11", 40, "98.10");
        assertThrows(ConfirmacaoAltoRiscoException.class, () -> ordens.criar(INVESTIDOR_A, semConfirmar));

        PedidoOrdem confirmando = new PedidoOrdem(UUID.randomUUID().toString(), "FICT11", TipoOrdem.COMPRA, 40,
                new BigDecimal("98.10"), true);
        assertEquals(StatusOrdem.EXECUTADA, ordens.criar(INVESTIDOR_A, confirmando).getStatus());
    }

    @Test
    @DisplayName("RN-07: Bolsa fora do ar gera FALHA sem mexer no saldo; a recuperação executa depois")
    void falhaERecuperacao() {
        parametros.atualizar("BOLSA_DISPONIVEL", "false");
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.COMPRA, "ORCP3", 10, "25.40"));
        assertEquals(StatusOrdem.FALHA, ordem.getStatus());
        assertEquals(new BigDecimal("10000.00"), contas.buscarSaldo(1));

        parametros.atualizar("BOLSA_DISPONIVEL", "true");
        assertEquals(1, ordens.recuperarFalhas(ADMIN));
        assertEquals(new BigDecimal("9746.00"), contas.buscarSaldo(1));
    }

    @Test
    @DisplayName("Cancelamento: só ordens que aguardam podem ser canceladas, e só pelo dono")
    void cancelamento() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.COMPRA, "ORCP3", 10, "20.00"));

        assertThrows(RegraNegocioException.class, () -> ordens.cancelar(INVESTIDOR_B, ordem.getId()));
        ordens.cancelar(INVESTIDOR_A, ordem.getId());
        assertThrows(RegraNegocioException.class, () -> ordens.cancelar(INVESTIDOR_A, ordem.getId()));
    }

    @Test
    @DisplayName("Atualização: novo preço que alcança a cotação executa a ordem")
    void alterarPreco() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.COMPRA, "ORCP3", 10, "20.00"));
        Ordem atualizada = ordens.alterarPreco(INVESTIDOR_A, ordem.getId(), new BigDecimal("25.40"));
        assertEquals(StatusOrdem.EXECUTADA, atualizada.getStatus());
    }

    @Test
    @DisplayName("RNF-07: registros de auditoria não podem ser alterados nem apagados")
    void auditoriaImutavel() {
        assertThrows(Exception.class, () -> jdbc.update("UPDATE auditoria SET acao = 'X'"));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM auditoria"));
    }

    @Test
    @DisplayName("RN-04: o banco impede que uma ordem executada volte de status")
    void bancoProtegeEstadoFinal() {
        Ordem ordem = ordens.criar(INVESTIDOR_A, pedido(TipoOrdem.COMPRA, "ORCP3", 1, "25.40"));
        assertThrows(Exception.class,
                () -> jdbc.update("UPDATE ordem SET status = 'CRIADA' WHERE id = ?", ordem.getId()));
    }
}
