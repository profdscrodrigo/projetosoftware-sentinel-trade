package br.mackenzie.sentineltrade.controller.form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/* Transforma os erros de validação em mensagens simples para mostrar na tela */
public final class MensagensDeErro {

    private static final Map<String, String> NOMES = Map.of(
            "ativo", "Ativo", "tipo", "Tipo", "quantidade", "Quantidade", "preco", "Preço");

    private MensagensDeErro() {
    }

    public static List<String> de(BindingResult resultado) {
        List<String> mensagens = new ArrayList<>();
        for (FieldError erro : resultado.getFieldErrors()) {
            String mensagem = erro.isBindingFailure()
                    ? "Valor inválido no campo " + NOMES.getOrDefault(erro.getField(), erro.getField()) + "."
                    : erro.getDefaultMessage();
            if (!mensagens.contains(mensagem)) {
                mensagens.add(mensagem);
            }
        }
        return mensagens;
    }
}
