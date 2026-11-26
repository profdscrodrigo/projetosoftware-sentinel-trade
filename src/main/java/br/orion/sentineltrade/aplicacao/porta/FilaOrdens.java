package br.orion.sentineltrade.aplicacao.porta;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Fila durável de ordens a transmitir (processamento assíncrono, com reagendamento). */
public interface FilaOrdens {
    void publicar(MensagemFila m);
    List<MensagemFila> disponiveis(Instant agora, int limite);
    void concluir(UUID mensagemId);
    void reagendar(UUID mensagemId, int tentativas, Instant proximaTentativa);
}
