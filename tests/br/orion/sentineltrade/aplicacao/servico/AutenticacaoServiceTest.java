package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.DadosDemonstracao;
import br.orion.sentineltrade.dominio.EventoAuditoria;
import br.orion.sentineltrade.dominio.Excecoes.*;
import br.orion.sentineltrade.dominio.Papel;
import br.orion.sentineltrade.dominio.TipoEvento;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AutenticacaoServiceTest {
    private static final String ANA = "ana@orion.example";
    private final Cenario c = new Cenario();

    private long eventos(TipoEvento tipo) {
        return c.app.persistencia.auditoria().listar(0, 10_000).stream().filter(e -> e.tipo().equals(tipo.name())).count();
    }

    @Test void loginComSenhaEMfaLiberaAcessoComOPerfilCorreto() {         // RF01 + RF02
        String token = c.ana();
        var sessao = c.app.auth.exigir(token, Papel.INVESTIDOR);
        assertEquals(c.anaId(), sessao.usuarioId());
        assertEquals(1, eventos(TipoEvento.LOGIN_SUCESSO));
    }

    @Test void senhaCorretaSemSegundoFatorNaoDaAcesso() {
        String pendente = c.app.auth.iniciarLogin(ANA, c.cred(ANA).senha());
        assertThrows(AutenticacaoException.class, () -> c.app.auth.exigir(pendente), "token pendente não é sessão");
    }

    @Test void senhaIncorretaOuUsuarioInexistenteRecebemMensagemIdentica() {   // sem enumeração de usuários
        String m1 = assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin(ANA, "senha-errada")).getMessage();
        String m2 = assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin("ninguem@orion.example", "qualquer")).getMessage();
        String m3 = assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin("isto não é e-mail", "x")).getMessage();
        assertEquals(m1, m2);
        assertEquals(m1, m3);
    }

    @Test void bloqueioTemporarioAposCincoFalhasSeguidas() {
        for (int i = 0; i < AutenticacaoService.MAX_FALHAS_LOGIN; i++)
            assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin(ANA, "senha-errada"));
        // mesmo com a senha CORRETA, a conta está bloqueada
        assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin(ANA, c.cred(ANA).senha()));
        assertEquals(1, eventos(TipoEvento.LOGIN_BLOQUEADO));
        c.relogio.avancar(AutenticacaoService.BLOQUEIO.plusMinutes(1));
        assertNotNull(c.ana(), "após o período de bloqueio o acesso é liberado");
    }

    @Test void tresCodigosMfaErradosInvalidamAPendencia() {
        String pendente = c.app.auth.iniciarLogin(ANA, c.cred(ANA).senha());
        for (int i = 0; i < AutenticacaoService.MAX_TENTATIVAS_MFA; i++)
            assertThrows(AutenticacaoException.class, () -> c.app.auth.confirmarMfa(pendente, c.codigoMfaErrado(ANA)));
        String certo = DadosDemonstracao.codigoMfaAtual(c.app, ANA);
        assertThrows(AutenticacaoException.class, () -> c.app.auth.confirmarMfa(pendente, certo), "pendência revogada");
        assertEquals(3, eventos(TipoEvento.MFA_FALHA));
    }

    @Test void codigoMfaMalFormadoEhRecusado() {
        String pendente = c.app.auth.iniciarLogin(ANA, c.cred(ANA).senha());
        for (String ruim : new String[]{"", "abc", "12345", "1234567", null, "123 45"})
            assertThrows(AutenticacaoException.class, () -> c.app.auth.confirmarMfa(pendente, ruim));
    }

    @Test void tokenPendenteSoServePraUmaConfirmacao() {
        String pendente = c.app.auth.iniciarLogin(ANA, c.cred(ANA).senha());
        String codigo = DadosDemonstracao.codigoMfaAtual(c.app, ANA);
        assertNotNull(c.app.auth.confirmarMfa(pendente, codigo));
        assertThrows(AutenticacaoException.class, () -> c.app.auth.confirmarMfa(pendente, codigo), "não reutilizável");
    }

    @Test void sessaoExpiraEInvalidaComLogout() {
        String token = c.ana();
        c.relogio.avancar(AutenticacaoService.TTL_SESSAO.plusSeconds(1));
        assertThrows(AutenticacaoException.class, () -> c.app.auth.exigir(token));

        String outro = c.ana();
        assertDoesNotThrow(() -> c.app.auth.exigir(outro));
        c.app.auth.encerrar(outro);
        assertThrows(AutenticacaoException.class, () -> c.app.auth.exigir(outro));
    }

    @Test void controleDeAcessoPorPerfilEhAplicadoEAuditado() {          // RNF01
        String token = c.ana();
        assertThrows(AcessoNegadoException.class, () -> c.app.auth.exigir(token, Papel.ADMINISTRADOR));
        assertThrows(AcessoNegadoException.class, () -> c.app.auth.exigir(token, Papel.ADMINISTRADOR, Papel.ANALISTA_RISCO));
        assertDoesNotThrow(() -> c.app.auth.exigir(token, Papel.ANALISTA_RISCO, Papel.INVESTIDOR));
        assertEquals(2, eventos(TipoEvento.ACESSO_NEGADO));
    }

    @Test void tokensInvalidosSaoRecusados() {
        for (String ruim : new String[]{null, "", "   ", "x".repeat(500), "token-forjado", "../../etc/passwd"})
            assertThrows(AutenticacaoException.class, () -> c.app.auth.exigir(ruim));
    }

    @Test void limiteDeTentativasDeLoginPorMinuto() {
        for (int i = 0; i < 10; i++) {
            try { c.app.auth.iniciarLogin("alvo@orion.example", "x"); } catch (AutenticacaoException esperado) { /* ok */ }
        }
        assertThrows(LimiteTaxaException.class, () -> c.app.auth.iniciarLogin("alvo@orion.example", "x"));
        c.relogio.avancar(Duration.ofMinutes(2));
        assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin("alvo@orion.example", "x"));
    }

    @Test void falhasDeLoginSaoAuditadasSemVazarSenha() {
        assertThrows(AutenticacaoException.class, () -> c.app.auth.iniciarLogin(ANA, "SenhaSecretaQueNaoDeveAparecer1!"));
        List<EventoAuditoria> todos = c.app.persistencia.auditoria().listar(0, 1000);
        assertEquals(1, eventos(TipoEvento.LOGIN_FALHA));
        for (EventoAuditoria e : todos) assertFalse(e.detalhes().contains("SenhaSecreta"));
    }

    @Test void hashDeSenhaESegredoMfaNaoVazamEmToString() {
        var u = c.app.persistencia.usuarios().buscarPorEmail(ANA).orElseThrow();
        assertFalse(u.toString().contains(u.senhaHash()));
        assertFalse(u.toString().contains(u.segredoMfa()));
        assertTrue(u.senhaHash().startsWith("pbkdf2_sha256$"), "senha armazenada somente como hash");
    }
}
