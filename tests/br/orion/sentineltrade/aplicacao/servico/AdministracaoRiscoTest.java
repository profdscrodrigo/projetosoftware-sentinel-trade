package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.dominio.*;
import br.orion.sentineltrade.dominio.Excecoes.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdministracaoRiscoTest {
    /** Senha de teste gerada a cada execução: nenhum segredo literal no repositório. */
    private static final String SENHA_TESTE = "Aa1!" + java.util.UUID.randomUUID();
    private final Cenario c = new Cenario();
    private static final String SENHA_FORTE = SENHA_TESTE;

    private long eventos(TipoEvento tipo) {
        return c.app.persistencia.auditoria().listar(0, 10_000).stream().filter(e -> e.tipo().equals(tipo.name())).count();
    }

    // ---- RF13: cadastro de investidores
    @Test void administradorCadastraInvestidorQueJaPodeAutenticarComMfa() {
        UUID id = c.app.admin.cadastrarInvestidor(c.admin(), "Carla Nova", "carla@orion.example", SENHA_FORTE);
        var conta = c.app.persistencia.contas().buscarPorInvestidor(id).orElseThrow();
        assertEquals(Dinheiro.ZERO, conta.saldoDisponivel());
        assertTrue(c.app.persistencia.limites().buscar(id).isPresent(), "limites padrão criados");
        String pend = c.app.auth.iniciarLogin("carla@orion.example", SENHA_FORTE);
        assertNotNull(c.app.auth.confirmarMfa(pend, DadosDemonstracao.codigoMfaAtual(c.app, "carla@orion.example")));
        assertEquals(1, eventos(TipoEvento.USUARIO_CADASTRADO) - 4, "além dos 4 usuários da carga inicial");
    }

    @Test void cadastroRecusaEmailDuplicadoSenhaFracaEEntradaInvalida() {
        String adm = c.admin();
        assertThrows(ConflitoException.class, () -> c.app.admin.cadastrarInvestidor(adm, "Outra Ana", "ana@orion.example", SENHA_FORTE));
        assertThrows(ValidacaoException.class, () -> c.app.admin.cadastrarInvestidor(adm, "Dani Fraca", "dani@orion.example", "123456"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.cadastrarInvestidor(adm, "<script>", "dani@orion.example", SENHA_FORTE));
        assertThrows(ValidacaoException.class, () -> c.app.admin.cadastrarInvestidor(adm, "Dani Ok", "dani@@orion", SENHA_FORTE));
        assertTrue(c.app.persistencia.usuarios().buscarPorEmail("dani@orion.example").isEmpty());
    }

    @Test void funcoesAdministrativasExigemPerfilAdministrador() {
        String inv = c.ana(), ris = c.risco();
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.cadastrarInvestidor(inv, "Eve Intrusa", "eve@orion.example", SENHA_FORTE));
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.depositarSimulado(inv, c.anaId().toString(), "1000"));
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.cadastrarAtivo(ris, "ABCD3", "Ativo Teste", "ACAO"));
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.alterarStatusAtivo(inv, "PETR4", "SUSPENSO"));
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.definirParametro(inv, ParametrosSistema.MERCADO_FORCAR_ABERTO, "true"));
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.consultarAuditoria(inv, 0, 10));
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.verificarIntegridadeAuditoria(ris));
    }

    @Test void depositoSimuladoCreditaESoAceitaValoresValidos() {
        String adm = c.admin(), ana = c.ana();
        c.app.admin.depositarSimulado(adm, c.anaId().toString(), "5000,50");
        assertEquals(Dinheiro.de("105000.50"), c.app.carteira.consultarSaldo(ana).disponivel());
        for (String ruim : new String[]{"-10", "0", "abc", "1e9", ""})
            assertThrows(ValidacaoException.class, () -> c.app.admin.depositarSimulado(adm, c.anaId().toString(), ruim));
        assertThrows(ValidacaoException.class, () -> c.app.admin.depositarSimulado(adm, "nao-e-uuid", "10"));
        assertThrows(NaoEncontradoException.class, () -> c.app.admin.depositarSimulado(adm, UUID.randomUUID().toString(), "10"));
    }

    // ---- RF12: ativos e parâmetros
    @Test void administradorGerenciaAtivosComAuditoria() {
        String adm = c.admin();
        c.app.admin.cadastrarAtivo(adm, "HGLG11", "CSHG Logistica", "FII");
        assertEquals(6, c.app.admin.listarAtivos(adm).size());
        assertThrows(ConflitoException.class, () -> c.app.admin.cadastrarAtivo(adm, "HGLG11", "Duplicado", "FII"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.cadastrarAtivo(adm, "HG11", "Ticker ruim", "FII"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.cadastrarAtivo(adm, "ABCD3", "Tipo ruim", "DERIVATIVO"));
        c.app.admin.alterarStatusAtivo(adm, "HGLG11", "SUSPENSO");
        assertThrows(ValidacaoException.class, () -> c.app.admin.alterarStatusAtivo(adm, "HGLG11", "TALVEZ"));
        assertThrows(NaoEncontradoException.class, () -> c.app.admin.alterarStatusAtivo(adm, "ZZZZ9", "ATIVO"));
        assertEquals(1, eventos(TipoEvento.ATIVO_CADASTRADO));
        assertEquals(1, eventos(TipoEvento.ATIVO_ALTERADO));
    }

    @Test void parametrosSoAceitamChavesPermitidasEValoresValidos() {
        String adm = c.admin();
        c.app.admin.definirParametro(adm, ParametrosSistema.ORDEM_FAIXA_PRECO_PCT, "5");
        assertEquals("5", c.app.admin.listarParametros(adm).get(ParametrosSistema.ORDEM_FAIXA_PRECO_PCT));
        assertEquals(1, eventos(TipoEvento.PARAMETRO_ALTERADO));
        assertThrows(ValidacaoException.class, () -> c.app.admin.definirParametro(adm, "sistema.comando", "rm -rf /"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.definirParametro(adm, ParametrosSistema.MERCADO_ABERTURA, "25:99"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.definirParametro(adm, ParametrosSistema.ORDEM_FAIXA_PRECO_PCT, "abc"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.definirParametro(adm, ParametrosSistema.ORDEM_FAIXA_PRECO_PCT, "500"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.definirParametro(adm, ParametrosSistema.MERCADO_FORCAR_ABERTO, "talvez"));
        assertThrows(ValidacaoException.class, () -> c.app.admin.definirParametro(adm, ParametrosSistema.COTACAO_IDADE_MAX_SEG, "0"));
    }

    @Test void faixaDePrecoParametrizadaMudaOComportamentoDaValidacao() {
        String adm = c.admin(), ana = c.ana();
        c.app.admin.definirParametro(adm, ParametrosSistema.ORDEM_FAIXA_PRECO_PCT, "1");
        assertEquals(CodigoRegra.PRECO_FORA_DA_FAIXA, c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 10, "37.00")).ordem().motivoRejeicao());
    }

    @Test void mercadoForcadoAbertoPermiteNegociarForaDoHorario() {
        c.relogio.definir(java.time.Instant.parse("2026-11-21T14:00:00Z"));      // sábado
        String adm = c.admin(), ana = c.ana();
        c.app.cotacoesSimuladas.atualizarInstantes();
        c.app.admin.definirParametro(adm, ParametrosSistema.MERCADO_FORCAR_ABERTO, "true");
        assertEquals(StatusOrdem.VALIDADA, c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 10, "38.50")).ordem().status());
    }

    // ---- RF15 / RF19: analista de risco
    @Test void analistaAjustaLimitesEOsNovosLimitesValemParaAProximaOrdem() {
        String ris = c.risco(), ana = c.ana();
        c.app.risco.definirLimites(ris, c.anaId().toString(), "1000", "5000", "500");
        assertEquals(Dinheiro.de("1000"), c.app.risco.consultarLimites(ris, c.anaId().toString()).valorMaximoPorOrdem());
        assertEquals(CodigoRegra.LIMITE_POR_ORDEM_EXCEDIDO, c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 100, "38.50")).ordem().motivoRejeicao());
        assertThrows(ConfirmacaoNecessariaException.class, () -> c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 20, "38.50")));   // 770 > 500
        assertEquals(1, eventos(TipoEvento.LIMITE_ALTERADO));
    }

    @Test void limitesIncoerentesOuDeNaoInvestidorSaoRecusados() {
        String ris = c.risco();
        assertThrows(ValidacaoException.class, () -> c.app.risco.definirLimites(ris, c.anaId().toString(), "5000", "1000", "500"));   // diário < por ordem
        assertThrows(ValidacaoException.class, () -> c.app.risco.definirLimites(ris, c.anaId().toString(), "1000", "5000", "2000"));  // confirmação > por ordem
        assertThrows(NaoEncontradoException.class, () -> c.app.risco.definirLimites(ris, c.id("admin@orion.example").toString(), "1000", "5000", "500"));
        assertThrows(AcessoNegadoException.class, () -> c.app.risco.definirLimites(c.ana(), c.anaId().toString(), "999999", "999999", "999999"));
    }

    @Test void analistaAcompanhaOrdensRejeitadas() {
        String ana = c.ana(), ris = c.risco();
        c.app.ordens.enviarOrdem(ana, Cenario.venda("PETR4", 10, "38.50"));              // rejeitada: sem posição
        c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 10, "38.50"));             // válida
        var rej = c.app.risco.ordensRejeitadas(ris);
        assertEquals(1, rej.size());
        assertEquals(CodigoRegra.POSICAO_INSUFICIENTE, rej.get(0).motivoRejeicao());
        assertEquals(2, c.app.risco.listarInvestidores(ris).size());
    }

    // ---- auditoria
    @Test void administradorConsultaEVerificaATrilhaDeAuditoria() {
        String adm = c.admin();
        c.app.ordens.enviarOrdem(c.ana(), Cenario.compra("PETR4", 10, "38.50"));
        var eventos = c.app.admin.consultarAuditoria(adm, 0, 100);
        assertFalse(eventos.isEmpty());
        assertTrue(c.app.admin.verificarIntegridadeAuditoria(adm).integra());
        assertEquals(3, c.app.admin.consultarAuditoria(adm, 0, 3).size());
    }
}
