package br.mackenzie.sentineltrade.controller;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import br.mackenzie.sentineltrade.controller.form.LoginForm;
import br.mackenzie.sentineltrade.controller.form.MfaForm;
import br.mackenzie.sentineltrade.model.Usuario;
import br.mackenzie.sentineltrade.seguranca.AutenticacaoService;
import br.mackenzie.sentineltrade.seguranca.DesafioMfa;

/* Login em duas etapas: senha e depois código MFA (RF-01) */
@Controller
public class LoginController {

    private static final String DESAFIO_NA_SESSAO = "desafioMfa";
    private static final String MENSAGEM_GENERICA =
            "E-mail ou senha inválidos. Depois de 3 tentativas erradas o acesso fica bloqueado por 15 minutos.";

    private final AutenticacaoService autenticacao;
    private final SecurityContextRepository repositorioContexto;

    public LoginController(AutenticacaoService autenticacao, SecurityContextRepository repositorioContexto) {
        this.autenticacao = autenticacao;
        this.repositorioContexto = repositorioContexto;
    }

    @GetMapping("/login")
    public String telaLogin(Model model) {
        model.addAttribute("loginForm", new LoginForm());
        return "login";
    }

    @PostMapping("/login")
    public String entrar(@Valid @ModelAttribute("loginForm") LoginForm form, BindingResult resultado,
                         HttpSession sessao, Model model) {
        Optional<Long> usuarioId = resultado.hasErrors()
                ? Optional.empty()
                : autenticacao.verificarSenha(form.getEmail(), form.getSenha());

        if (usuarioId.isEmpty()) {
            form.setSenha(null);   // a senha nunca volta para a tela
            model.addAttribute("erro", MENSAGEM_GENERICA);
            return "login";
        }
        sessao.setAttribute(DESAFIO_NA_SESSAO, autenticacao.criarDesafio(usuarioId.get()));
        return "redirect:/mfa";
    }

    @GetMapping("/mfa")
    public String telaMfa(HttpSession sessao, Model model) {
        DesafioMfa desafio = (DesafioMfa) sessao.getAttribute(DESAFIO_NA_SESSAO);
        if (desafio == null) {
            return "redirect:/login";
        }
        prepararTelaMfa(desafio, model);
        return "mfa";
    }

    @PostMapping("/mfa")
    public String verificarMfa(@Valid @ModelAttribute("mfaForm") MfaForm form, BindingResult resultado,
                               HttpServletRequest request, HttpServletResponse response,
                               Model model, RedirectAttributes flash) {
        HttpSession sessao = request.getSession();
        DesafioMfa desafio = (DesafioMfa) sessao.getAttribute(DESAFIO_NA_SESSAO);
        if (desafio == null) {
            return "redirect:/login";
        }

        String codigo = resultado.hasErrors() ? null : form.getCodigo();
        DesafioMfa.Resultado verificacao = desafio.verificar(codigo, Instant.now());
        sessao.setAttribute(DESAFIO_NA_SESSAO, desafio);

        switch (verificacao) {
            case OK -> {
                sessao.removeAttribute(DESAFIO_NA_SESSAO);
                Usuario usuario = autenticacao.concluirLogin(desafio.getUsuarioId());
                entrarNoSistema(usuario, request, response);
                return "redirect:" + usuario.getPaginaInicial();
            }
            case CODIGO_ERRADO -> {
                model.addAttribute("erro", "Código inválido. Tentativas restantes: "
                        + desafio.getTentativasRestantes() + ".");
                prepararTelaMfa(desafio, model);
                return "mfa";
            }
            default -> {
                sessao.removeAttribute(DESAFIO_NA_SESSAO);
                autenticacao.registrarFalhaMfa(desafio.getUsuarioId());
                flash.addFlashAttribute("erro", "Código expirado ou tentativas esgotadas. Faça o login novamente.");
                return "redirect:/login";
            }
        }
    }

    private void prepararTelaMfa(DesafioMfa desafio, Model model) {
        if (!model.containsAttribute("mfaForm")) {
            model.addAttribute("mfaForm", new MfaForm());
        }
        model.addAttribute("codigoSimulado", desafio.getCodigo());
        model.addAttribute("tentativasRestantes", desafio.getTentativasRestantes());
    }

    /** Guarda o usuário logado na sessão, com um novo id de sessão (evita fixação de sessão) */
    private void entrarNoSistema(Usuario usuario, HttpServletRequest request, HttpServletResponse response) {
        request.changeSessionId();
        UsernamePasswordAuthenticationToken autenticado = UsernamePasswordAuthenticationToken.authenticated(
                usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name())));
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticado);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, request, response);
    }
}
