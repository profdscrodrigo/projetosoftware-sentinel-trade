package br.mackenzie.sentineltrade.auditoria;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.mackenzie.sentineltrade.model.RegistroAuditoria;
import br.mackenzie.sentineltrade.repository.AuditoriaRepository;
import br.mackenzie.sentineltrade.seguranca.Sanitizador;

/* Registra QUEM fez O QUÊ, QUANDO e em QUAL registro (RF-11). Nunca grava senhas, códigos de acesso ou tokens */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaRepository repositorio;

    public AuditoriaService(AuditoriaRepository repositorio) {
        this.repositorio = repositorio;
    }

    public void registrar(Long usuarioId, String acao, String entidade, Long entidadeId, String detalhes) {
        String detalhesLimpos = Sanitizador.paraLog(detalhes);
        repositorio.inserir(usuarioId, acao, entidade, entidadeId, detalhesLimpos);
        log.info("AUDITORIA acao={} entidade={} id={} usuario={}", acao, entidade, entidadeId, usuarioId);
    }

    public List<RegistroAuditoria> listarRecentes(int limite) {
        return repositorio.listarRecentes(limite);
    }
}
