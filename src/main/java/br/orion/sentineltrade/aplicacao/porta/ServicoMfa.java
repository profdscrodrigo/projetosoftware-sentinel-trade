package br.orion.sentineltrade.aplicacao.porta;

import java.time.Instant;

/** Segundo fator de autenticação (simulado por TOTP, RFC 6238). */
public interface ServicoMfa {
    String gerarSegredo();
    boolean verificar(String segredo, String codigo, Instant agora);
    String codigoAtual(String segredo, Instant agora);   // usado apenas pelo autenticador simulado da demonstração
}
