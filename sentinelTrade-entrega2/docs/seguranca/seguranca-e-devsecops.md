# Segurança e DevSecOps — SentinelTrade

Este documento mostra **como o SentinelTrade se protege** e **onde isso está no código**:

1. Caso de uso com notação de segurança (UMLsec)
2. Sanitização de dados
3. Bibliotecas de segurança
4. Esteira DevSecOps

> Todo o sistema é **simulado**: nenhum dado pessoal real, nenhuma senha real e nenhuma bolsa real são usados.

---

## 1. Caso de uso com notação de segurança (UMLsec)

Diagrama: [Casos de Uso com segurança](../diagramas/SentinelTrade_UML_Casos_de_Uso_Seguranca.drawio.png)

| Estereótipo | Onde aparece | O que significa no SentinelTrade |
| :--- | :--- | :--- |
| `«secure»` | Sistema SentinelTrade | O sistema inteiro segue as regras de segurança abaixo. |
| `«secureAuthentication»` | Autenticar-se com MFA | Senha (BCrypt) + código de 6 dígitos; bloqueio após 3 erros. |
| `«integrity»` | Enviar ordem | Saldo, carteira, limites e mercado conferidos no servidor; sem ordem duplicada. |
| `«availability»` | Recuperar ordens com falha | Bolsa fora do ar não corrompe a carteira; a ordem é reenviada depois. |
| `«secrecy»` | Proteger dados | Senhas só como hash, sessão protegida, segredos fora do Git. |
| `«inputValidation»` | Validar e sanitizar entradas | Toda entrada é validada no servidor antes de ser usada. |
| `«leastPrivilege»` | Autorizar por perfil | Cada perfil só acessa as próprias telas. |
| `«audit»` | Registrar auditoria | Quem, quando, o quê e o resultado, sem dados sensíveis. |

---

## 2. Sanitização de dados

| Cuidado | Como fizemos | Arquivo |
| :--- | :--- | :--- |
| Validar no servidor antes de processar, gravar ou encaminhar | `@Valid` em todos os formulários; o controller só continua se não houver erro | `controller/form/*.java`, controllers |
| Definir formato, tamanho, tipo e faixa dos campos | Código do ativo `^[A-Z]{4}[0-9]{1,2}$`, quantidade 1–100.000, preço R$ 0,01–1.000.000 com 2 casas, e-mail com até 120 caracteres, código MFA com 6 dígitos | `OrdemForm`, `LoginForm`, `MfaForm` |
| Allowlist (aceitar só valores previstos) | Tipo da ordem é `enum` (COMPRA/VENDA); status do filtro só aceita valores do `enum`; o ativo precisa existir no banco; só 2 parâmetros podem ser alterados pela tela | `TipoOrdem`, `StatusOrdem.deTexto()`, `MercadoService` |
| Consultas parametrizadas | Todo SQL usa `?` com `JdbcTemplate`; nenhum dado do usuário é concatenado no SQL | `repository/*.java` |
| Codificar a saída | As telas usam `th:text` do Thymeleaf, que transforma `<`, `>` e aspas em texto seguro (evita XSS) | `templates/*.html` |
| Sanitizar HTML só se o sistema aceitar HTML | O sistema **não aceita HTML** de usuário: tudo é texto simples | — |
| Rejeitar caminhos, URLs e comandos vindos do usuário | Nenhuma entrada vira caminho de arquivo, URL ou comando | — |
| Limitar tamanho, tentativas e frequência | Formulário até 16 KB; login bloqueia por 15 min após 3 senhas erradas; MFA aceita 3 tentativas e expira em 5 min | `application.properties`, `AutenticacaoService`, `DesafioMfa` |
| Tratar dados de APIs externas como não confiáveis | A resposta da Bolsa é conferida (só "EXECUTADA" ou "PENDENTE" são aceitas); qualquer outra vira falha | `RespostaBolsa`, `OrdemService` |
| Não usar funções perigosas (`eval`) | Nenhum código é executado a partir de texto | — |
| Logs sem dados sensíveis | Senhas e códigos nunca vão para o log; quebras de linha são removidas (evita "log injection") | `seguranca/Sanitizador`, `auditoria/AuditoriaService` |
| Erros sem detalhes internos | O usuário vê só uma mensagem e um código; o detalhe fica no log do servidor | `TratadorDeErros`, `error.html` |

---

## 3. Bibliotecas de segurança

| Biblioteca | Para que usamos |
| :--- | :--- |
| **Spring Security** | Proteção de rotas por perfil, proteção CSRF nos formulários, cabeçalhos de segurança (CSP, bloqueio de iframe), sessão segura e senhas com **BCrypt**. |
| **Jakarta Bean Validation** (Hibernate Validator) | Validação declarativa dos campos (`@NotBlank`, `@Pattern`, `@Positive`, `@Digits`...). |
| **JdbcTemplate** (Spring JDBC) | Consultas parametrizadas, sem concatenar dados no SQL. |
| **Thymeleaf** | Codificação automática da saída HTML (`th:text`), evitando XSS. Por isso o OWASP Java Encoder não foi necessário. |
| **SLF4J / Logback** | Logs organizados, sem dados sensíveis. |
| **Trivy** (na esteira) | Procura vulnerabilidades conhecidas nas bibliotecas (como o OWASP Dependency-Check) e senhas/chaves esquecidas no código. |
| **Semgrep** (na esteira) | Analisa o código Java em busca de padrões perigosos (SQL injection, XSS etc.). |
| **Dependabot** (GitHub) | Avisa e propõe atualização quando uma biblioteca fica desatualizada ou vulnerável. |

---

## 4. Esteira DevSecOps

Arquivo: [`.github/workflows/devsecops.yml`](../../.github/workflows/devsecops.yml) — roda sozinha a cada envio (push) ou pull request no GitHub.

```
 push / pull request
        │
        ├─ 1. Compilar e rodar os testes ........ mvn verify (regras de validação, estados, MFA, segurança web)
        ├─ 2. Dependências e segredos ........... Trivy: bloqueia vulnerabilidade CRÍTICA ou senha/chave exposta
        └─ 3. Análise do código ................. Semgrep: bloqueia falhas graves (SQL injection, XSS...)
                    │
          tudo verde ✔ → pode aprovar e publicar      algum vermelho ✘ → corrigir antes
```

Para ver o resultado: no GitHub, aba **Actions** → clique na execução mais recente.

---

## 5. Perguntas de verificação de segurança

| Pergunta | Resposta no SentinelTrade |
| :--- | :--- |
| Qual dado entra e qual allowlist o valida? | Ativo (regex + precisa existir), tipo (enum), quantidade e preço (faixas), e-mail, senha, código MFA (6 dígitos), filtro de status (enum), parâmetros do admin (lista fechada). |
| A entrada pode causar SQL Injection, XSS, Command Injection, SSRF...? | Não: SQL sempre parametrizado, saída codificada pelo Thymeleaf, nenhuma entrada vira comando, URL ou caminho. |
| O usuário está autenticado e autorizado no servidor? | Sim: Spring Security confere login e perfil em toda requisição; o investidor só acessa as próprias ordens. |
| O menor privilégio possível foi aplicado? | Sim: 3 perfis com rotas separadas (`/investidor`, `/admin`, `/risco`). |
| Dados pessoais, credenciais e chaves estão protegidos? | Senhas só como hash BCrypt; `.env` fora do Git; somente dados fictícios. |
| Há auditoria suficiente e segura? | Sim: login, MFA, ordens, cotações, parâmetros e limites são auditados; a tabela não aceita UPDATE nem DELETE. |
| As falhas expõem detalhes internos? | Não: o usuário vê só uma mensagem genérica com um código de erro. |
| As dependências foram verificadas? | Sim: Trivy na esteira + Dependabot. |
| Há testes unitários, de integração e de segurança? | Sim: regras de validação, transição de estados, MFA, ciclo completo das ordens com banco real e testes de acesso/CSRF (pasta `tests/`). |
| A esteira bloqueia segredos, SAST, SCA e testes? | Sim: os 3 passos da esteira falham se houver problema. |
| A API externa é validada? | Sim: a resposta da Bolsa é conferida; se a Bolsa cair, a ordem vira FALHA e nada é alterado na carteira. |
| Existe plano de monitoramento, incidentes e rollback? | Básico: auditoria e logs para investigar; recuperação de ordens com falha; para voltar uma versão, publica-se a versão anterior aprovada pela esteira, e o banco (um único arquivo) pode ter cópias de segurança. |

---

## 6. Limitações (por ser um projeto acadêmico simulado)

- O **MFA é simulado**: o código aparece num "celular simulado" na tela. Num sistema real chegaria por SMS ou aplicativo autenticador.
- O **HTTPS** é usado em produção; no computador local o site roda em `http://localhost:8080`.
- A **Bolsa** é simulada dentro da aplicação (classe `BolsaSimulada`), atrás da interface `BolsaGateway`.
- O cadastro de investidores, contas e carteiras (RF-02, RF-03, RF-04) vem pronto do `seed.sql`; essas telas de cadastro não foram feitas nesta fase.
