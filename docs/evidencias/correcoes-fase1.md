# Revisão da Fase 1 e correções

Revisamos o que entregamos na Fase 1 comparando com o enunciado e com a devolutiva (`FEEDBACK_FASE_1.md`):
*"consolidar requisitos funcionais e não funcionais, critérios de aceitação, regras de negócio, rastreabilidade e o
diagrama de casos de uso no repositório."* Depois, fizemos uma **segunda revisão crítica** da nossa própria entrega e corrigimos
o que encontramos. Este documento registra as duas revisões.

## 1. Atendimento direto à devolutiva

| Pedido | Situação na Fase 1 | Agora |
|---|---|---|
| Requisitos funcionais e não funcionais consolidados | Lista curta no README, sem os IDs do enunciado e sem prioridade | [`requisitos.md`](../requisitos/requisitos.md): 23 RF + 12 RNF, IDs do enunciado preservados, mapeamento dos antigos |
| Critérios de aceitação | Inexistentes | Em cada requisito, verificáveis e ligados a testes; RNFs com números **medidos** |
| Regras de negócio | Inexistentes | [`regras-de-negocio.md`](../requisitos/regras-de-negocio.md): RN01–RN32, com código, local e teste |
| Rastreabilidade | Inexistente | [`matriz-rastreabilidade.md`](../requisitos/matriz-rastreabilidade.md): requisito → caso de uso → diagrama → classe → tabela → teste |
| Diagrama de casos de uso no repositório | Imagem hospedada fora do repositório, não editável | Diagrama **versionado e editável** em `docs/uml/casos-de-uso/`, com verificador de cruzamentos |

## 2. Problemas que encontramos no que existia

| # | Problema | Correção |
|---|---|---|
| 1 | Casos de uso com apenas 3 dos 7 atores obrigatórios | 7 atores |
| 2 | Faltavam casos de uso obrigatórios (MFA, saldo, gerenciar ativos/limites, verificar saldo/posição) | Incluídos |
| 3 | «include» semanticamente trocados (Transmitir → Acompanhar ciclo de vida; Notificação ligada ao Investidor) | Relações refeitas conforme o enunciado |
| 4 | Nenhum «extend» | «extend» de confirmação de alto valor |
| 5 | "Enviar ordem" sem distinguir compra e venda | Separados (UC05/UC06) |
| 6 | README em Python; o enunciado exige Java | Java 21 |
| 7 | Banco de dados "a definir" | PostgreSQL + `schema.sql`, `seed.sql`, `hardening-postgres.sql` |
| 8 | Sem `.gitignore`, `.env.example`, `src/`, `tests/`, `database/`, `docs/requisitos`, `diagrams/` | Estrutura do enunciado criada |
| 9 | `.DS_Store` versionado (lixo de macOS, pode vazar nomes de pastas) | Removidos e ignorados |
| 10 | RNFs sem critério mensurável | Critério e evidência por RNF |
| 11 | UML: apenas 1 dos 7 tipos | Os 7 tipos |
| 12 | Nenhum código, banco ou teste | Núcleo Java + JDBC + SQL + testes automatizados |

## 3. Segunda revisão crítica (autocrítica da nossa entrega)

Depois de montar a solução, a revisamos como o avaliador faria e encontramos 7 fragilidades. O que fizemos com cada uma:

| # | Fragilidade | O que fizemos | Situação |
|---|---|---|---|
| 1 | Testes escritos, mas executados só com um executor próprio compatível com JUnit 5 (o ambiente de montagem não tinha Maven) | Executamos a suíte com Maven em nossa máquina de desenvolvimento em 26/11/2026. | **Feito:** 169 testes executados, 0 falhas, 0 erros e 0 ignorados; `BUILD SUCCESS`. Evidência: `resultado-testes-maven-postgresql-2026-11-26.txt` |
| 2 | Ainda não havíamos executado a camada PostgreSQL contra um banco real | Executamos os testes de integração contra o banco descartável `sentineltrade_test`, incluindo fluxo completo, persistência após reinicialização e concorrência real. | **Feito:** os 2 testes de `PostgresIntegracaoTest` passaram; estão incluídos no total de 169 acima. |
| 3 | Só havia interface de console | Criamos **API REST + interface web** (servidor HTTP do JDK, sem dependências), testadas por 14 testes de API, 6 de JSON e por um teste em navegador real (10 passos, sem erro de console/CSP). | Feito |
| 4 | Segredo MFA em texto puro no banco | **AES-256-GCM** com chave em variável de ambiente; com PostgreSQL o sistema recusa iniciar sem a chave; valor adulterado nunca autentica. | Feito |
| 5 | Sem ordem a mercado e **sem alteração de ordem** — o enunciado pede "criação, consulta, **atualização** e cancelamento" | Implementamos **alteração (cancelar e substituir)** e **ordem a mercado** (preço de proteção). | Feito |
| 6 | Alguns documentos ainda tinham instruções genéricas, em vez de registrar o que fizemos | Revisamos a documentação para registrar as implementações, decisões técnicas e resultados efetivamente observados. | Feito |
| 7 | Diagrama de casos de uso com linhas cruzando elipses | Gerador com **verificador geométrico**: 0 de 45 linhas atravessam elipse alheia. A atribuição de "gerenciar limites" ao Administrador **e** ao Analista segue a tabela de atores do enunciado; ajustamos o texto do RF15 para ficar coerente com o diagrama e o código. | Feito |

Também, na revisão, um dos novos testes revelou uma falha real nossa: `String.trim()` removia caracteres de controle nas pontas
(`"PETR4\u0000"` era aceito como `PETR4`). Passamos a usar `strip()` e a **rejeitar** controles em vez de normalizá-los (teste de regressão em `ValidacoesTest`).

## 4. Verificações realizadas (26/11/2026)

- [x] Executar `mvn clean test`: **169 testes, 0 falhas, 0 erros e 0 ignorados**.
- [x] Executar os dois testes de integração contra PostgreSQL real, em banco separado `sentineltrade_test`.
- [x] Configurar `MFA_ENCRYPTION_KEY` localmente e executar a aplicação com `SENTINEL_STORAGE=jdbc`.
- [x] Testar na interface web login/MFA, compra, venda, alteração, cancelamento e persistência após reiniciar a aplicação.
- [x] Versionar workflow de CI com PostgreSQL 16, testes, Gitleaks e OWASP Dependency-Check em `.github/workflows/ci.yml`.

## 5. O que foi acrescentado

- **Código Java 21** (arquitetura hexagonal, sem framework no núcleo): domínio, serviços de aplicação, 18 portas, adaptadores (memória, JDBC), integrações simuladas, console, API REST e interface web.
- **Banco relacional:** 12 tabelas com `CHECK`, `UNIQUE`, chaves estrangeiras e índices; carga de referência; endurecimento do PostgreSQL.
- **Segurança:** PBKDF2, TOTP (vetores da RFC 6238), segredo MFA cifrado, RBAC, allowlist, auditoria encadeada por hash, limitação de taxa, CSP.
- **Resiliência:** fila durável, *timeout*, *circuit breaker*, recuo exponencial, idempotência nas duas pontas, *outbox*.
- **Evidências:** saída dos 169 testes, dos dois testes PostgreSQL, da demonstração, do SQL, do desempenho e da interface web (com capturas de tela).
