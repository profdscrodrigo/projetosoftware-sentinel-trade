package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.dominio.Excecoes.*;
import br.orion.sentineltrade.dominio.Ordem;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** RNF08 / RN08: cada investidor só enxerga e altera o que é seu; perfis não se misturam. */
class PrivacidadeEAcessoTest {
    private final Cenario c = new Cenario();

    @Test void investidorNaoConsultaNemCancelaOrdemDeOutro() {
        String ana = c.ana(), bruno = c.bruno();
        Ordem daAna = c.app.ordens.enviarOrdem(ana, Cenario.compra("PETR4", 10, "38.50")).ordem();
        String id = daAna.id().toString();
        assertThrows(NaoEncontradoException.class, () -> c.app.ordens.consultarOrdem(bruno, id));
        assertThrows(NaoEncontradoException.class, () -> c.app.ordens.cancelarOrdem(bruno, id));
        assertThrows(NaoEncontradoException.class, () -> c.app.ordens.execucoesDaOrdem(bruno, id));
        assertEquals(0, c.app.ordens.historico(bruno).size());
        assertEquals(1, c.app.ordens.historico(ana).size());
        // para quem não é o dono, uma ordem real e uma inexistente são indistinguíveis
        String m1 = assertThrows(NaoEncontradoException.class, () -> c.app.ordens.consultarOrdem(bruno, id)).getMessage();
        String m2 = assertThrows(NaoEncontradoException.class, () -> c.app.ordens.consultarOrdem(bruno, UUID.randomUUID().toString())).getMessage();
        assertEquals(m1, m2);
        assertEquals(StatusOrdemEsperado.VALIDADA, StatusOrdemEsperado.de(c.app.ordens.consultarOrdem(ana, id)), "a ordem de Ana segue intacta");
    }

    private enum StatusOrdemEsperado { VALIDADA, OUTRO;
        static StatusOrdemEsperado de(Ordem o) { return o.status().name().equals("VALIDADA") ? VALIDADA : OUTRO; } }

    @Test void carteiraESaldoSaoSempreDaSessaoENuncaDeParametro() {
        var daAna = c.app.carteira.consultarCarteira(c.ana());
        var doBruno = c.app.carteira.consultarCarteira(c.bruno());
        assertEquals("VALE3", daAna.itens().get(0).ticker().codigo());
        assertEquals("PETR4", doBruno.itens().get(0).ticker().codigo());
    }

    @Test void notificacoesSaoPrivadas() {
        String ana = c.ana(), bruno = c.bruno();
        c.app.ordens.enviarOrdem(ana, Cenario.venda("PETR4", 10, "38.50"));              // rejeitada → notificação da Ana
        assertEquals(1, c.app.notificacoes.minhasNotificacoes(ana).size());
        assertEquals(0, c.app.notificacoes.minhasNotificacoes(bruno).size());
    }

    @Test void perfisNaoInvestidoresNaoOperamNemConsultamCarteira() {
        String adm = c.admin(), ris = c.risco();
        for (String t : new String[]{adm, ris}) {
            assertThrows(AcessoNegadoException.class, () -> c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 1, "38.50")));
            assertThrows(AcessoNegadoException.class, () -> c.app.carteira.consultarSaldo(t));
            assertThrows(AcessoNegadoException.class, () -> c.app.carteira.consultarCarteira(t));
            assertThrows(AcessoNegadoException.class, () -> c.app.ordens.historico(t));
        }
    }

    @Test void semAutenticacaoNadaFunciona() {
        for (String ruim : new String[]{null, "", "token-falso"}) {
            assertThrows(AutenticacaoException.class, () -> c.app.ordens.enviarOrdem(ruim, Cenario.compra("PETR4", 1, "38.50")));
            assertThrows(AutenticacaoException.class, () -> c.app.carteira.consultarCarteira(ruim));
            assertThrows(AutenticacaoException.class, () -> c.app.admin.listarAtivos(ruim));
        }
    }

    @Test void acessosNegadosFicamAuditadosComIdentificacaoDoAtor() {
        String ana = c.ana();
        assertThrows(AcessoNegadoException.class, () -> c.app.admin.consultarAuditoria(ana, 0, 10));
        var negados = c.app.persistencia.auditoria().listar(0, 1000).stream().filter(e -> e.tipo().equals("ACESSO_NEGADO")).toList();
        assertEquals(1, negados.size());
        assertEquals(c.anaId(), negados.get(0).usuarioId());
    }
}
