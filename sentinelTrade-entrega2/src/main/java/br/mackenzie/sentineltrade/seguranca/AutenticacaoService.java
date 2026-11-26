package br.mackenzie.sentineltrade.seguranca;

import java.util.Locale;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.mackenzie.sentineltrade.auditoria.AuditoriaService;
import br.mackenzie.sentineltrade.model.Usuario;
import br.mackenzie.sentineltrade.repository.ParametroRepository;
import br.mackenzie.sentineltrade.repository.UsuarioRepository;
import br.mackenzie.sentineltrade.repository.UsuarioRepository.DadosLogin;

/** Login em duas etapas: 1) e-mail + senha (BCrypt)  2) código MFA simulado (RF-01, RNF-01). Nunca informa QUAL dado estava errado. */
@Service
public class AutenticacaoService {

    /** Hash de uma senha qualquer: usado para o tempo de resposta ser igual quando o e-mail não existe. */
    private static final String HASH_PARA_IGUALAR_TEMPO = "$2a$10$8JO396OMmMeeI9MUyVnwZOfRSzOfJfEfAlbch/qESqL7PYYbPujVG";

    private final UsuarioRepository usuarios;
    private final ParametroRepository parametros;
    private final PasswordEncoder codificadorSenha;
    private final AuditoriaService auditoria;

    public AutenticacaoService(UsuarioRepository usuarios, ParametroRepository parametros,
                               PasswordEncoder codificadorSenha, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.parametros = parametros;
        this.codificadorSenha = codificadorSenha;
        this.auditoria = auditoria;
    }

    /** Etapa 1: confere e-mail e senha. Devolve o id do usuário ou vazio. */
    public Optional<Long> verificarSenha(String email, String senha) {
        String emailNormalizado = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        Optional<DadosLogin> encontrado = usuarios.buscarDadosLogin(emailNormalizado);

        if (encontrado.isEmpty()) {
            codificadorSenha.matches(senha, HASH_PARA_IGUALAR_TEMPO);
            auditoria.registrar(null, "LOGIN_FALHOU", "usuario", null, "Credenciais inválidas");
            return Optional.empty();
        }
        DadosLogin dados = encontrado.get();
        if (!dados.ativo() || dados.bloqueado()) {
            auditoria.registrar(dados.id(), "LOGIN_BLOQUEADO", "usuario", dados.id(), "Usuário inativo ou bloqueado");
            return Optional.empty();
        }
        if (!codificadorSenha.matches(senha, dados.senhaHash())) {
            usuarios.registrarFalhaLogin(dados.id(), parametros.inteiro("MAX_TENTATIVAS_LOGIN", 3),
                    parametros.inteiro("MINUTOS_BLOQUEIO_LOGIN", 15));
            auditoria.registrar(dados.id(), "LOGIN_FALHOU", "usuario", dados.id(), "Senha incorreta");
            return Optional.empty();
        }
        return Optional.of(dados.id());
    }

    /** Etapa 2 (início): cria o código MFA. */
    public DesafioMfa criarDesafio(long usuarioId) {
        auditoria.registrar(usuarioId, "MFA_CODIGO_GERADO", "usuario", usuarioId, null);
        return DesafioMfa.novo(usuarioId);
    }

    /** Etapa 2 (fim): código correto, o usuário entra no sistema. */
    public Usuario concluirLogin(long usuarioId) {
        usuarios.zerarTentativas(usuarioId);
        auditoria.registrar(usuarioId, "LOGIN_SUCESSO", "usuario", usuarioId, null);
        return usuarios.buscarPorId(usuarioId)
                .orElseThrow(() -> new IllegalStateException("Usuário não encontrado."));
    }

    public void registrarFalhaMfa(long usuarioId) {
        auditoria.registrar(usuarioId, "MFA_FALHOU", "usuario", usuarioId, "Código expirado ou tentativas esgotadas");
    }
}
