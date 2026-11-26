package br.orion.sentineltrade.aplicacao.servico;

import br.orion.sentineltrade.aplicacao.porta.Repositorios.AuditoriaRepositorio;
import br.orion.sentineltrade.dominio.EventoAuditoria;
import br.orion.sentineltrade.dominio.Excecoes.ConcorrenciaException;
import br.orion.sentineltrade.dominio.TipoEvento;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trilha de auditoria append-only com encadeamento de hash (SHA-256): cada evento carrega o hash do anterior,
 * então qualquer alteração, remoção ou reordenação posterior é detectável por {@link #verificarIntegridade()}.
 */
public final class AuditoriaService {
    public static final String GENESIS = "0".repeat(64);

    public record Verificacao(boolean integra, long seqProblema, String motivo) {}

    private final AuditoriaRepositorio repo;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepositorio repo, Clock clock) { this.repo = repo; this.clock = clock; }

    /** Deve ser chamado DENTRO da transação do fato auditado: ambos confirmam ou desfazem juntos. */
    public EventoAuditoria registrar(TipoEvento tipo, UUID usuarioId, String entidade, String entidadeId, String detalhes) {
        String det = Sanitizador.paraLog(detalhes, 500);
        for (int tentativa = 1; ; tentativa++) {
            repo.bloquearAppend();                                               // dentro da transação: uma ponta por vez
            Instant agora = clock.instant().truncatedTo(ChronoUnit.MILLIS);  // precisão do banco: evita divergência de hash
            EventoAuditoria ultimo = repo.ultimo().orElse(null);
            long seq = ultimo == null ? 1 : ultimo.seq() + 1;
            String anterior = ultimo == null ? GENESIS : ultimo.hash();
            String hash = calcularHash(anterior, seq, agora, tipo.name(), usuarioId, entidade, entidadeId, det);
            EventoAuditoria e = new EventoAuditoria(seq, agora, tipo.name(), usuarioId, entidade, entidadeId, det, anterior, hash);
            try {
                repo.anexar(e);
                return e;
            } catch (ConcorrenciaException c) {                               // outro processo gravou a mesma seq: relê a ponta da cadeia
                if (tentativa >= 5) throw c;
            }
        }
    }

    static String calcularHash(String anterior, long seq, Instant instante, String tipo, UUID usuarioId,
                               String entidade, String entidadeId, String detalhes) {
        StringBuilder sb = new StringBuilder();
        for (String campo : new String[]{anterior, String.valueOf(seq), instante.toString(), tipo,
                usuarioId == null ? "-" : usuarioId.toString(), entidade, entidadeId, detalhes}) {
            sb.append(campo.length()).append(':').append(campo).append(';');   // prefixo de tamanho: sem ambiguidade de campos
        }
        return Hashes.sha256Hex(sb.toString());
    }

    /** Verifica a cadeia inteira, página a página. */
    public Verificacao verificarIntegridade() {
        List<EventoAuditoria> todos = new ArrayList<>();
        long ultimoSeq = 0;
        while (true) {
            List<EventoAuditoria> pagina = repo.listar(ultimoSeq, 1000);
            if (pagina.isEmpty()) break;
            todos.addAll(pagina);
            ultimoSeq = pagina.get(pagina.size() - 1).seq();
        }
        return verificarCadeia(todos);
    }

    /** Função pura (testável): valida sequência, encadeamento e hash de cada evento. */
    public static Verificacao verificarCadeia(List<EventoAuditoria> eventos) {
        String esperadoAnterior = GENESIS;
        long esperadoSeq = 1;
        for (EventoAuditoria e : eventos) {
            if (e.seq() != esperadoSeq) return new Verificacao(false, e.seq(), "Sequência quebrada (evento removido ou inserido)");
            if (!e.hashAnterior().equals(esperadoAnterior)) return new Verificacao(false, e.seq(), "Encadeamento quebrado");
            String recalculado = calcularHash(e.hashAnterior(), e.seq(), e.instante(), e.tipo(), e.usuarioId(), e.entidade(), e.entidadeId(), e.detalhes());
            if (!recalculado.equals(e.hash())) return new Verificacao(false, e.seq(), "Conteúdo adulterado");
            esperadoAnterior = e.hash();
            esperadoSeq++;
        }
        return new Verificacao(true, 0, "Cadeia íntegra (" + eventos.size() + " eventos)");
    }

    public List<EventoAuditoria> listar(long aposSeq, int limite) { return repo.listar(aposSeq, Math.min(Math.max(limite, 1), 500)); }
}
