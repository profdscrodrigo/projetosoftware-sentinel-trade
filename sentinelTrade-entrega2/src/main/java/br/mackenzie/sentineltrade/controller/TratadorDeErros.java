package br.mackenzie.sentineltrade.controller;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import br.mackenzie.sentineltrade.model.Usuario;

/* Tratamento seguro de exceções: o usuário vê só uma mensagem simples e um código; os detalhes técnicos ficam apenas no log do servidor */
@ControllerAdvice
public class TratadorDeErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    /** Deixa o usuário logado disponível em todas as telas (para o menu do topo) */
    @ModelAttribute("usuarioLogado")
    public Usuario usuarioLogado(@AuthenticationPrincipal Usuario usuario) {
        return usuario;
    }

    @ExceptionHandler({NoResourceFoundException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpRequestMethodNotSupportedException.class})
    public ModelAndView pedidoInvalido(Exception e) {
        log.warn("Pedido inválido: {}", e.getClass().getSimpleName());
        ModelAndView tela = new ModelAndView("error", HttpStatus.NOT_FOUND);
        tela.addObject("titulo", "Página não encontrada");
        tela.addObject("mensagem", "O endereço não existe ou o pedido é inválido.");
        return tela;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView erroInesperado(Exception e) {
        String codigo = UUID.randomUUID().toString().substring(0, 8);
        log.error("Erro inesperado [codigo={}]", codigo, e);
        ModelAndView tela = new ModelAndView("error", HttpStatus.INTERNAL_SERVER_ERROR);
        tela.addObject("titulo", "Algo deu errado");
        tela.addObject("mensagem", "Não foi possível concluir a operação. Tente novamente.");
        tela.addObject("codigo", codigo);
        return tela;
    }
}
