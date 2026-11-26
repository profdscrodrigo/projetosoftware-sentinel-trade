# Sistema SentinelTrade
Gustavo Leal Gomes - RA: 10738069

Manuela Atanes Garabedian - RA: 10735908

## Visão do Projeto

O SentinelTrade é um sistema de negociação financeira de alta criticidade desenvolvido para a Orion Capital, uma corretora de investimentos fictícia. O objetivo é permitir que investidores consultem cotações, enviem e acompanhem ordens de compra e venda de ativos com segurança, rastreabilidade e confiabilidade.

Todo o ambiente é simulado: os ativos, as contas, as cotações e a integração com a bolsa/corretora são fictícios. Nenhuma operação envolve bolsa real ou dinheiro real.

### Sobre a Orion Capital

A Orion Capital é uma corretora fictícia que precisa de uma plataforma para intermediar operações de seus investidores com o mercado. Por lidar com operações financeiras, a corretora exige que o sistema garanta autenticação forte, controle de limites, prevenção de erros e registro completo de todas as ações para fins de auditoria.

### Resumo

| Item | Descrição |
| :--- | :--- |
| Nome do projeto | **SentinelTrade** |
| Integrantes | Gustavo Leal Gomes (RA 10738069) · Manuela Atanes Garabedian (RA 10735908) |
| O que é | Um **site** em que investidores entram com senha + código MFA, consultam saldo, carteira e cotações e enviam ordens de compra e venda com segurança e sem duplicidade |
| Linguagem | **Java 17** com Spring Boot |
| Banco de dados | **SQLite** (banco relacional em SQL · scripts em [`database/`](./database/)) |
| Telas | HTML + CSS (Thymeleaf), abertas pelo navegador |
| Requisitos | 18 funcionais (RF-01 a RF-18) e 9 não funcionais (RNF-01 a RNF-09) |
| Regras de negócio | 15 (RN-01 a RN-15) |
| Casos de uso | 19 (UC01 a UC19) |
| Diagramas | 9 (casos de uso, segurança, componentes, classes, estados, atividades, sequência e implantação) |
| Testes automáticos | 40 testes, rodados a cada envio ao GitHub |

### Sumário

1. [Atores do sistema](#atores-do-sistema)
2. [Como acessar o site](#como-acessar-o-site)
3. [Roteiro para testar](#roteiro-para-testar)
4. [O que o site faz](#o-que-o-site-faz)
5. [Diagramas](#diagramas)
6. [Documentação](#documentação)
7. [Segurança e DevSecOps](#segurança-e-devsecops)
8. [Testes automáticos](#testes-automáticos)
9. [Tecnologias](#tecnologias)
10. [Organização das pastas](#organização-das-pastas)

---

## Atores do sistema

| Ator | Papel | Onde aparece no site |
|------|-------|-------|
| **Investidor** | Consulta cotações, envia ordens e acompanha seu andamento | Painel, Nova ordem e Minhas ordens |
| **Administrador** | Gerencia investidores, contas, carteiras, limites e recuperação de operações | Mercado (cotações, Bolsa, recuperação) e Auditoria |
| **Analista de Risco** | Consulta e ajusta limites de risco e acompanha ordens bloqueadas ou rejeitadas | Painel de risco |
| **Bolsa/Corretora simulada** | Sistema externo que fornece cotações e executa as ordens | Simulada dentro do sistema (classe `BolsaSimulada`) |
| **Provedor de Cotações** | Fornece os valores simulados dos ativos | Cotações alteradas pelo administrador |
| **Serviço de Notificação** | Informa o investidor sobre execução, rejeição, cancelamento ou falha de ordens | Quadro "Notificações" no painel do investidor |
| **Serviço de Auditoria** | Mantém o registro de ações, decisões e alterações de estado | Tela Auditoria (administrador) |

---

## Como acessar o site

O site roda no seu próprio computador. **Precisa ter:** o [IntelliJ IDEA Community](https://www.jetbrains.com/idea/download/) (gratuito) e internet na primeira vez, para baixar as bibliotecas.

| Passo | O que fazer |
| :---: | :--- |
| 1 | Baixe o projeto: no GitHub, botão verde **Code → Download ZIP**, e extraia o arquivo. |
| 2 | No IntelliJ: **File → Open** e selecione a pasta que contém o arquivo `pom.xml`. |
| 3 | Espere o IntelliJ baixar as bibliotecas (barra de progresso no canto de baixo). Se aparecer sublinhado vermelho no código, clique no ícone **m** (Maven), na barra da direita, e depois no botão de recarregar (**Reload All Maven Projects**). |
| 4 | Se o IntelliJ pedir o Java: **File → Project Structure → SDK → Download JDK → versão 17**. |
| 5 | Abra `src/main/java/br/mackenzie/sentineltrade/SentinelTradeApplication.java` e clique no **▶ verde** ao lado de `public class`. |
| 6 | Espere aparecer `Started SentinelTradeApplication` no painel de baixo e abra no navegador: **http://localhost:8080** |
| 7 | Entre com um dos usuários abaixo e digite o **código de 6 dígitos** que aparece no "celular simulado" da tela. |
| 8 | Para desligar o site, clique no **quadrado vermelho (Stop)** do IntelliJ. |

**Usuários de demonstração (fictícios)** — a senha de todos é `Demo@1234`

| Perfil | E-mail | O que vê depois de entrar |
| :--- | :--- | :--- |
| Investidor A | `investidor.a@email.exemplo` | Saldo de R$ 10.000, carteira, cotações, ordens e notificações |
| Investidor B | `investidor.b@email.exemplo` | A mesma tela, mas só com os dados da conta dele |
| Administrador | `admin@orioncapital.exemplo` | Mercado (cotações, Bolsa, recuperação de ordens) e Auditoria |
| Analista de risco | `risco@orioncapital.exemplo` | Limites de cada conta e ordens rejeitadas |

**Observações**

- Na primeira execução o banco `database/sentineltrade.db` é criado sozinho a partir do `schema.sql` e do `seed.sql`. Para começar do zero, feche o site e apague esse arquivo.
- Errar a senha 3 vezes bloqueia o usuário por 15 minutos. Para liberar na hora, apague o arquivo do banco.
- O arquivo `.env` é opcional: os valores padrão já funcionam no computador. O [`.env.example`](./.env.example) mostra só os nomes das variáveis.
- Pelo terminal, com Maven instalado: `mvn spring-boot:run` abre o site e `mvn test` roda os testes.

---

## Roteiro para testar

| # | Entre como | Faça | O que deve acontecer |
| :---: | :--- | :--- | :--- |
| 1 | Investidor A | *Nova ordem* → compre 10 **ORCP3** a R$ 25,40 | Ordem **Executada**: o saldo diminui e a carteira aumenta |
| 2 | Investidor A | Compre ORCP3 a **R$ 20,00** | Ordem fica **Enviada** (aguardando, porque o preço está abaixo da cotação) |
| 3 | Administrador | Em *Mercado*, mude a cotação de ORCP3 para **R$ 19,50** | A ordem que aguardava é **executada** sozinha |
| 4 | Administrador → Investidor A | Clique em **Simular queda** da Bolsa e envie uma ordem como investidor | Ordem vira **Falha**, sem mexer no saldo |
| 5 | Administrador | Clique em **Restabelecer** e depois em **Recuperar ordens** | A ordem com falha é reenviada e executada, sem duplicar |
| 6 | Investidor A | Tente vender mais do que tem ou comprar acima do limite | Ordem **Rejeitada**, com o motivo na tela |
| 7 | Investidor A | Em *Minhas ordens*, altere o preço ou cancele uma ordem que aguarda | Preço atualizado ou ordem **Cancelada**; executadas não podem ser canceladas |
| 8 | Analista de risco | Veja as ordens rejeitadas e altere os limites de uma conta | Os novos limites passam a valer nas próximas ordens |
| 9 | Administrador | Abra **Auditoria** | Tudo o que aconteceu aparece registrado (quem, quando, o quê) |

---

## O que o site faz

| Tela | Quem usa | O que dá para fazer | Requisitos |
| :--- | :--- | :--- | :--- |
| Login + código MFA | Todos | Entrar com senha e código de 6 dígitos; bloqueio após 3 erros | RF-01, RNF-01 |
| Painel do investidor | Investidor | Ver saldo, carteira (quantidade, preço médio, valor), cotações e notificações | RF-06, RF-12, RF-15 |
| Nova ordem | Investidor | Comprar ou vender um ativo; confirmar ordens de alto risco | RF-07, RF-08, RF-14 |
| Minhas ordens | Investidor | Ver as ordens, filtrar por status, ver o histórico, alterar o preço e cancelar | RF-07, RF-10, RF-16 |
| Mercado | Administrador | Alterar cotações, abrir/fechar o mercado, simular queda da Bolsa, recuperar ordens com falha | RF-06, RF-09, RF-13, RF-17 |
| Auditoria | Administrador | Ver o registro de tudo o que aconteceu (não pode ser alterado nem apagado) | RF-11, RNF-02, RNF-07 |
| Painel de risco | Analista de risco | Ajustar os limites de cada conta e acompanhar as ordens rejeitadas | RF-05, RF-18 |
| *(por trás de todas as telas)* | Sistema | Validar saldo, posição, limites, mercado e ativo; impedir ordem duplicada; enviar à Bolsa simulada | RF-08, RF-09, RF-14 |

Os investidores, contas e carteiras (RF-02, RF-03, RF-04) já vêm cadastrados com dados fictícios pelo [`seed.sql`](./database/seed.sql); as telas de cadastro não fazem parte desta versão.

---

## Diagramas

Todos ficam em [`docs/diagramas/`](./docs/diagramas/). Clique para abrir. Cada diagrama tem a imagem (`.png`) e o arquivo editável do [draw.io](https://app.diagrams.net) (`.drawio`), que abre direto no editor pelo link da tabela.

| Diagrama | O que mostra | draw.io |
| :--- | :--- | :--- |
| [Casos de Uso](./docs/diagramas/SentinelTrade_UML_Casos_de_Uso_Completo.png) | Todos os atores e casos de uso, com «include» e «extend» | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Casos_de_Uso_Completo.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Casos_de_Uso_Completo.drawio) |
| [Casos de Uso com segurança (UMLsec)](./docs/diagramas/SentinelTrade_UML_Casos_de_Uso_Seguranca.png) | Qual proteção vale em cada caso de uso | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Casos_de_Uso_Seguranca.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Casos_de_Uso_Seguranca.drawio) |
| [Componentes](./docs/diagramas/SentinelTrade_UML_Componentes.png) | As partes do sistema e quem usa quem | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Componentes.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Componentes.drawio) |
| [Classes](./docs/diagramas/SentinelTrade_UML_Classes.png) | As principais classes do código | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Classes.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Classes.drawio) |
| [Estados](./docs/diagramas/SentinelTrade_UML_Estados.png) | Por quais status uma ordem passa | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Estados.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Estados.drawio) |
| [Atividades](./docs/diagramas/SentinelTrade_UML_Atividades.png) | O passo a passo de enviar uma ordem | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Atividades.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Atividades.drawio) |
| [Sequência](./docs/diagramas/SentinelTrade_UML_Sequencia.png) | As mensagens trocadas numa compra de ativo | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Sequencia.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Sequencia.drawio) |
| [Implantação](./docs/diagramas/SentinelTrade_UML_Implantacao.png) | Em quais máquinas o sistema roda | [Abrir](https://app.diagrams.net/#Hmanuelagarabedian7%2FsentinelTrade_teste%2Fmain%2Fdocs%2Fdiagramas%2FSentinelTrade_UML_Implantacao.drawio) · [Baixar](https://github.com/manuelagarabedian7/sentinelTrade_teste/raw/main/docs/diagramas/SentinelTrade_UML_Implantacao.drawio) |

---

## Documentação

| Documento | O que tem |
|---|---|
| [Requisitos](./docs/requisitos/requisitos.md) | Requisitos funcionais (18) e não funcionais (9), com critérios de aceitação |
| [Regras de negócio](./docs/requisitos/regras-de-negocio.md) | As 15 regras que toda operação deve respeitar |
| [Matriz de rastreabilidade](./docs/requisitos/matriz-rastreabilidade.md) | Cada requisito ligado ao caso de uso, diagrama, código e teste |
| [Casos de uso](./docs/casos-de-uso/casos-de-uso.md) | Descrição dos casos de uso UC01 a UC19 |
| [Casos de uso críticos](./docs/casos-de-uso/casos-de-uso-criticos.md) | Passo a passo de Enviar ordem de compra, Enviar ordem de venda e Cancelar ordem |
| [Segurança e DevSecOps](./docs/seguranca/seguranca-e-devsecops.md) | UMLsec, sanitização de dados, bibliotecas de segurança e esteira |
| [Diagramas](./docs/diagramas/) | Os 9 diagramas UML |
| [Código-fonte](./src/README.md) | Organização do código em camadas e onde estão os conceitos de orientação a objetos |
| [Testes](./tests/README.md) | O que cada teste automático verifica |
| [Banco de dados](./database/) | [`schema.sql`](./database/schema.sql) (tabelas, restrições e gatilhos) e [`seed.sql`](./database/seed.sql) (dados fictícios) |
| [Evidências](./docs/evidencias/) | Prints de estrutura, commits e participação dos integrantes |

---

## Segurança e DevSecOps

| Proteção | Como funciona | Onde está |
| :--- | :--- | :--- |
| Login forte | Senha guardada só como hash BCrypt + código MFA de 6 dígitos; bloqueio após 3 erros | `seguranca/` |
| Acesso por perfil | Cada perfil só abre as próprias telas; o investidor só vê a própria conta | `seguranca/SegurancaConfig` |
| Validação de entradas | Todo campo tem formato, tamanho e faixa conferidos no servidor | `controller/form/` |
| Sem SQL Injection | Todo SQL usa parâmetros (`?`), nunca texto do usuário colado | `repository/` |
| Sem XSS | As telas transformam `<`, `>` e aspas em texto seguro | `templates/` |
| Formulários protegidos | Token CSRF em todo formulário e cabeçalhos de segurança (CSP) | `seguranca/SegurancaConfig` |
| Erros sem detalhes internos | O usuário vê só uma mensagem e um código de erro | `controller/TratadorDeErros` |
| Auditoria imutável | Gatilhos do banco impedem alterar ou apagar registros | `database/schema.sql` |
| Esteira automática | A cada envio ao GitHub: testes + Trivy (bibliotecas vulneráveis e senhas expostas) + Semgrep (código perigoso) | [`.github/workflows/devsecops.yml`](./.github/workflows/devsecops.yml) |
| Bibliotecas atualizadas | O Dependabot avisa quando uma biblioteca precisa de atualização | [`.github/dependabot.yml`](./.github/dependabot.yml) |

Explicação completa: [Segurança e DevSecOps](./docs/seguranca/seguranca-e-devsecops.md). Para ver o resultado da esteira: aba **Actions** do GitHub.

---

## Testes automáticos

São 40 testes na pasta [`tests/`](./tests/). Eles rodam no IntelliJ (botão direito na pasta `tests` → **Run 'All Tests'**), com `mvn test` ou sozinhos na esteira do GitHub.

| Teste | O que verifica |
| :--- | :--- |
| `StatusOrdemTest` | Mudanças de status permitidas e proibidas |
| `OrdemTest` | Ordem começa como Criada, dados inválidos são recusados |
| `ValidadorOrdemTest` | Saldo, posição, limites, mercado e ativo |
| `OrdemServiceTest` | Ciclo completo com banco real: execução, duplicidade, falha e recuperação, cancelamento, auditoria |
| `ContaServiceTest` | Saldo, carteira, histórico só da própria conta, notificações e limites do analista |
| `SegurancaBasicaTest` | Código MFA (certo, errado, expirado) e limpeza do log |
| `SegurancaWebTest` | Login obrigatório, perfil errado bloqueado, CSRF e cabeçalhos de segurança |

---

## Tecnologias

| Tecnologia | Uso |
| :--- | :--- |
| Java 17 + Spring Boot 3.5 | Site e regras do sistema |
| Spring Security | Login, perfis de acesso, proteção CSRF, sessão segura e senhas com BCrypt |
| Jakarta Bean Validation | Validação dos campos dos formulários |
| Thymeleaf + HTML + CSS | Telas do site |
| SQLite + Spring JDBC | Banco de dados relacional com consultas parametrizadas |
| JUnit 5 + Spring Boot Test | Testes automáticos |
| Maven | Bibliotecas e build (`pom.xml`) |
| GitHub Actions + Trivy + Semgrep + Dependabot | Esteira DevSecOps |
| Git + GitHub | Versionamento e entregas (fork + Pull Request) |
| draw.io | Diagramas UML |

---

## Organização das pastas

```
sentineltrade/
├── README.md
├── pom.xml                       # bibliotecas do projeto (Maven)
├── .gitignore
├── .env.example                  # nomes das variáveis, sem valores
├── .github/
│   ├── workflows/devsecops.yml   # esteira DevSecOps
│   └── dependabot.yml            # atualização automática de bibliotecas
├── docs/
│   ├── requisitos/               # requisitos, regras de negócio e matriz de rastreabilidade
│   ├── casos-de-uso/             # UC01 a UC19 e casos de uso críticos
│   ├── diagramas/                # todos os diagramas UML (.png + .drawio)
│   ├── seguranca/                # sanitização, bibliotecas de segurança e DevSecOps
│   └── evidencias/               # prints de commits e participação
├── src/main/
│   ├── java/br/mackenzie/sentineltrade/
│   │   ├── controller/           # recebem os pedidos do navegador
│   │   ├── service/              # regras de negócio (ordens, contas, mercado, validações)
│   │   ├── seguranca/            # login, MFA e permissões
│   │   ├── auditoria/            # registro de auditoria
│   │   ├── notificacao/          # avisos ao investidor
│   │   ├── integracao/           # Bolsa simulada
│   │   ├── repository/           # acesso ao banco (SQL)
│   │   └── model/                # classes do domínio (Ordem, Usuario, Ativo...)
│   └── resources/                # telas HTML (templates), CSS e configuração
├── tests/                        # testes automáticos
└── database/
    ├── schema.sql                # tabelas, restrições e gatilhos
    └── seed.sql                  # dados fictícios para teste
```

---

  - `uml: inclui diagrama de casos de uso`
  - `feat: cria entidade Ordem`
  - `db: adiciona estrutura inicial de contas e ordens`
- Nunca versionar senhas, tokens, chaves, arquivos `.env` reais ou dados pessoais.
