package br.mackenzie.sentineltrade.model;

import java.util.Optional;

/** Estados da ordem e as mudanças permitidas entre eles (diagrama de estados). Regra RN-04: EXECUTADA, REJEITADA e CANCELADA são estados finais. */
public enum StatusOrdem {
    CRIADA("Criada"),
    VALIDADA("Validada"),
    ENVIADA("Aguardando execução"),
    EXECUTADA("Executada"),
    REJEITADA("Rejeitada"),
    CANCELADA("Cancelada"),
    FALHA("Falha no envio");

    private final String descricao;

    StatusOrdem(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /** Diz se a ordem pode passar deste estado para o estado "novo". */
    public boolean podeMudarPara(StatusOrdem novo) {
        return switch (this) {
            case CRIADA -> novo == VALIDADA || novo == REJEITADA;
            case VALIDADA -> novo == ENVIADA || novo == FALHA;
            case ENVIADA -> novo == EXECUTADA || novo == REJEITADA || novo == CANCELADA;
            case FALHA -> novo == ENVIADA || novo == CANCELADA;
            case EXECUTADA, REJEITADA, CANCELADA -> false;
        };
    }

    public boolean isFinal() {
        return this == EXECUTADA || this == REJEITADA || this == CANCELADA;
    }

    /** Estados que geram notificação para o investidor (RF-12). */
    public boolean notificaInvestidor() {
        return this == EXECUTADA || this == REJEITADA || this == CANCELADA || this == FALHA;
    }

    /** Converte um texto vindo da tela em status, aceitando só valores conhecidos (allowlist). */
    public static Optional<StatusOrdem> deTexto(String texto) {
        for (StatusOrdem status : values()) {
            if (status.name().equals(texto)) {
                return Optional.of(status);
            }
        }
        return Optional.empty();
    }
}
