package br.mackenzie.sentineltrade.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Regras de segurança do site (Spring Security):
 * - quem pode acessar cada página (menor privilégio por perfil);
 * - proteção CSRF nos formulários (ligada por padrão);
 * - cabeçalhos de segurança (CSP, bloqueio de iframe, etc.);
 * - senhas com BCrypt.
 */
@Configuration
@EnableWebSecurity
public class SegurancaConfig {

    @Bean
    public SecurityFilterChain filtroDeSeguranca(HttpSecurity http, SecurityContextRepository repositorioContexto)
            throws Exception {
        http
            .authorizeHttpRequests(acesso -> acesso
                .requestMatchers("/login", "/mfa", "/css/**", "/error").permitAll()
                .requestMatchers("/investidor/**").hasRole("INVESTIDOR")
                .requestMatchers("/admin/**").hasRole("ADMINISTRADOR")
                .requestMatchers("/risco/**").hasRole("ANALISTA_RISCO")
                .anyRequest().authenticated())
            .securityContext(contexto -> contexto.securityContextRepository(repositorioContexto))
            .exceptionHandling(erros -> erros
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
                .accessDeniedPage("/acesso-negado"))
            .logout(saida -> saida
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?saiu"))
            .headers(cabecalhos -> cabecalhos
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; form-action 'self'; base-uri 'self'")));
        return http.build();
    }

    /** Senhas guardadas só como hash BCrypt (RNF-01). */
    @Bean
    public PasswordEncoder codificadorDeSenha() {
        return new BCryptPasswordEncoder();
    }

    /** Onde o usuário logado fica guardado: na sessão do servidor. */
    @Bean
    public SecurityContextRepository repositorioDeContexto() {
        return new HttpSessionSecurityContextRepository();
    }
}
