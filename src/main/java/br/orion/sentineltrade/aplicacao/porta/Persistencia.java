package br.orion.sentineltrade.aplicacao.porta;

import br.orion.sentineltrade.aplicacao.porta.Repositorios.*;

/** Conjunto de adaptadores de persistência (memória ou JDBC) entregue ao composition root. */
public record Persistencia(UsuarioRepositorio usuarios, SessaoRepositorio sessoes, ContaRepositorio contas,
                           PosicaoRepositorio posicoes, AtivoRepositorio ativos, OrdemRepositorio ordens,
                           ExecucaoRepositorio execucoes, LimiteRiscoRepositorio limites, AuditoriaRepositorio auditoria,
                           NotificacaoRepositorio notificacoes, ParametroRepositorio parametros, FilaOrdens fila,
                           GerenciadorTransacao transacao) {}
