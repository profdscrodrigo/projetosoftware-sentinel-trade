package br.orion.sentineltrade.aplicacao.servico;

import static org.junit.jupiter.api.Assertions.*;

import br.orion.sentineltrade.Cenario;
import br.orion.sentineltrade.dominio.EventoAuditoria;
import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import br.orion.sentineltrade.dominio.TipoEvento;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuditoriaServiceTest {
    private final Cenario c = new Cenario();

    private List<EventoAuditoria> gerarEventos() {
        String t = c.ana();
        c.app.ordens.enviarOrdem(t, Cenario.compra("PETR4", 10, "38.50"));
        c.app.ordens.enviarOrdem(t, Cenario.compra("VALE3", 10, "62.40"));
        c.app.processador.ciclo();
        return c.app.persistencia.auditoria().listar(0, 10_000);
    }

    @Test void cadeiaDeAuditoriaEstaIntegraAposOperacoes() {                // RNF03
        List<EventoAuditoria> ev = gerarEventos();
        assertTrue(ev.size() >= 8, "login, criação, validação, envio e execução geram eventos");
        assertTrue(c.app.auditoria.verificarIntegridade().integra());
        assertEquals(AuditoriaService.GENESIS, ev.get(0).hashAnterior());
        for (int i = 1; i < ev.size(); i++) assertEquals(ev.get(i - 1).hash(), ev.get(i).hashAnterior());
    }

    @Test void alteracaoDeQualquerCampoEhDetectada() {
        List<EventoAuditoria> ev = gerarEventos();
        int k = ev.size() / 2;
        EventoAuditoria o = ev.get(k);
        List<EventoAuditoria> adulterada = new ArrayList<>(ev);
        adulterada.set(k, new EventoAuditoria(o.seq(), o.instante(), o.tipo(), o.usuarioId(), o.entidade(), o.entidadeId(),
                o.detalhes() + " (editado)", o.hashAnterior(), o.hash()));
        var r = AuditoriaService.verificarCadeia(adulterada);
        assertFalse(r.integra());
        assertEquals(o.seq(), r.seqProblema());

        adulterada.set(k, new EventoAuditoria(o.seq(), o.instante(), TipoEvento.ORDEM_CANCELADA.name(), o.usuarioId(), o.entidade(),
                o.entidadeId(), o.detalhes(), o.hashAnterior(), o.hash()));
        assertFalse(AuditoriaService.verificarCadeia(adulterada).integra());
    }

    @Test void remocaoEReordenacaoSaoDetectadas() {
        List<EventoAuditoria> ev = gerarEventos();
        List<EventoAuditoria> semUm = new ArrayList<>(ev);
        semUm.remove(3);
        assertFalse(AuditoriaService.verificarCadeia(semUm).integra());

        List<EventoAuditoria> troca = new ArrayList<>(ev);
        Collections.swap(troca, 2, 3);
        assertFalse(AuditoriaService.verificarCadeia(troca).integra());

        List<EventoAuditoria> semOPrimeiro = new ArrayList<>(ev);
        semOPrimeiro.remove(0);
        assertFalse(AuditoriaService.verificarCadeia(semOPrimeiro).integra());
    }

    @Test void trilhaEhSomenteInsercaoENaoAceitaSequenciaRepetida() {
        List<EventoAuditoria> ev = gerarEventos();
        EventoAuditoria primeiro = ev.get(0);
        assertThrows(ConcorrenciaException.class, () -> c.app.persistencia.auditoria().anexar(primeiro));
    }

    @Test void detalhesSaoSanitizadosContraForjaDeLog() {
        EventoAuditoria e = c.app.auditoria.registrar(TipoEvento.LOGIN_FALHA, null, "TESTE", "1", "linha1\nFAKE: LOGIN_SUCESSO\r\u001b[31m");
        assertFalse(e.detalhes().contains("\n") || e.detalhes().contains("\r") || e.detalhes().contains("\u001b"));
        assertTrue(c.app.auditoria.verificarIntegridade().integra());
    }

    @Test void mudancasDeEstadoDaOrdemGeramAuditoria() {                    // RN05
        gerarEventos();
        var tipos = c.app.persistencia.auditoria().listar(0, 10_000).stream().map(EventoAuditoria::tipo).toList();
        for (TipoEvento t : new TipoEvento[]{TipoEvento.ORDEM_CRIADA, TipoEvento.ORDEM_VALIDADA, TipoEvento.ORDEM_ENVIADA, TipoEvento.ORDEM_EXECUTADA})
            assertTrue(tipos.contains(t.name()), "faltou evento " + t);
    }
}
