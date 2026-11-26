package br.mackenzie.sentineltrade.web;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** Testa as proteções do site: login obrigatório, perfis, CSRF e cabeçalhos de segurança. */
@SpringBootTest(properties = "DB_PATH=target/teste-web.db")
@AutoConfigureMockMvc
class SegurancaWebTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Sem login, o investidor é mandado para a tela de login")
    void exigeLogin() throws Exception {
        mvc.perform(get("/investidor"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Investidor não acessa a área do administrador (menor privilégio)")
    void investidorNaoEntraNoAdmin() throws Exception {
        mvc.perform(get("/admin").with(user("investidor").roles("INVESTIDOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Formulário sem token CSRF é bloqueado")
    void exigeCsrf() throws Exception {
        mvc.perform(post("/login").param("email", "a@b.com").param("senha", "x"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Senha errada mostra mensagem genérica (não diz qual dado errou)")
    void loginComSenhaErrada() throws Exception {
        mvc.perform(post("/login").with(csrf())
                        .param("email", "investidor.a@email.exemplo").param("senha", "senha-errada"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("Tela de login tem os cabeçalhos de segurança (CSP e proteção contra iframe)")
    void cabecalhosDeSeguranca() throws Exception {
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "DENY"));
    }
}
