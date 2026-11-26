package br.mackenzie.sentineltrade.notificacao;

import java.util.List;

import org.springframework.stereotype.Service;

import br.mackenzie.sentineltrade.model.Notificacao;
import br.mackenzie.sentineltrade.model.Ordem;
import br.mackenzie.sentineltrade.repository.NotificacaoRepository;

/** Avisa o investidor sobre execução, rejeição, cancelamento ou falha de ordens (RF-12). */
@Service
public class NotificacaoService {

    private final NotificacaoRepository repositorio;

    public NotificacaoService(NotificacaoRepository repositorio) {
        this.repositorio = repositorio;
    }

    public void notificarOrdem(Ordem ordem) {
        String inicio = "Sua ordem de " + ordem.getTipo() + " de " + ordem.getQuantidade() + " "
                + ordem.getCodigoAtivo();
        String texto = switch (ordem.getStatus()) {
            case EXECUTADA -> inicio + " foi executada.";
            case REJEITADA -> inicio + " foi rejeitada: " + ordem.getMotivo();
            case CANCELADA -> inicio + " foi cancelada.";
            case FALHA -> inicio + " não chegou à Bolsa (fora do ar) e será reenviada na recuperação.";
            default -> inicio + " mudou para " + ordem.getStatus().getDescricao() + ".";
        };
        repositorio.inserirParaConta(ordem.getContaId(), ordem.getId(), texto);
    }

    public List<Notificacao> recentes(long investidorId) {
        return repositorio.listarRecentes(investidorId, 5);
    }
}
