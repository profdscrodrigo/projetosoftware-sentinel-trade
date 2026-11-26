package br.orion.sentineltrade.aplicacao.porta;

public interface HasherSenha {
    String hash(char[] senha);
    boolean verifica(char[] senha, String hashArmazenado);
}
