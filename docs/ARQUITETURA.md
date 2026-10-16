# Arquitetura técnica: android_state

Cliente Android nativo do CRUD de unidades federativas (estados), sobre a API do projeto `estado`. Mesmo comportamento do `flutter_state` e do `react_native_state`.

## 1. Stack

Kotlin 2.4, Jetpack Compose com Material 3, Navigation Compose, AGP 9 (Kotlin embutido), OkHttp 5, `kotlinx.serialization.json` lido como árvore (sem classes `@Serializable`), corrotinas e `StateFlow`. Sem framework de injeção de dependência.

## 2. Estrutura

```
core/     ApiClient (OkHttp), TokenStore, Jwt, Config, Uuid, Attempt, SessionEvents, KeystoreKeyValueStore
data/     Repositories (interfaces + implementações), Validation, FederativeUnit, JsonAccess
auth/     AuthController, ExpiryTimer
state/    StatesStore
ui/       EstadosRoot (navegação), Theme, components/, screens/
```

Composição manual: `AppContainer` cria as dependências e `AppDependencies` as expõe à UI. A direção é UI, state/auth, data, core; é convenção (não há teste de arquitetura).

## 3. Estado e fluxo de dados

Cada tela observa um `StateFlow` e emite eventos para um objeto de estado (`StatesStore`, `AuthController`), que chama o repositório. O repositório usa `ApiClient`, converte JSON em `FederativeUnit` e devolve erros tipados. A UI nunca fala com a rede.

`attempt{}` envolve chamadas suspensas e transforma falhas em resultado **sem engolir cancelamento** (`CancellationException` é relançada). `ExpiryTimer` agenda o fim da sessão pela claim `exp` do JWT.

## 4. Rede

Um interceptor do OkHttp aplica, em ordem: `X-Request-Id` (mantido entre tentativas), `Authorization: Bearer`, retry de GET (2 tentativas, com backoff) e, em 401, encerra a sessão uma única vez. O `request_id` do erro é mostrado ao usuário. A validação (campos, tamanhos, regras da UF) fica em `Validation` e repete as mensagens do back-end.

## 5. Segurança

- Token guardado com AES-GCM; a chave fica no **AndroidKeyStore** (`KeystoreKeyValueStore`), não exportável.
- `data_extraction_rules.xml` e `backup_rules.xml` excluem o armazenamento do backup.
- Tráfego claro só no build `e2e` e só para localhost. Release usa HTTPS.
- R8 minifica o release; o build `e2e` é um release minificado assinado com a chave de debug, para testar o que é publicado.

## 6. Navegação e deep links

Navigation Compose com grafo em `EstadosRoot`. Deep links: `estados://state/new` e `estados://state/{id}/edit`. Para o Maestro, `testTagsAsResourceId` expõe as tags como ids; diálogos são janelas separadas e não recebem os ids, então os fluxos tocam por texto.

## 7. Testes

- 143 testes: JUnit, `MockWebServer3`, Robolectric com Compose. Cobertura 98% de linhas (Kover, piso 90, exclusões documentadas).
- No JDK 25 o Robolectric precisa de `--add-exports/--add-opens`; as telas rodam em qualificadores altos (`w411dp-h2400dp`) para que listas preguiçosas materializem todas as linhas.
- Harness em `support/` (`AppHarness`, `Fakes`, `Support`).
- **Acessibilidade automática**: `assertAccessible` (alvo de toque de 48 dp e rótulo em todo controle) roda em 12 telas e estados, com controles negativos, mais um teste de contraste WCAG da paleta. O `enableAccessibilityChecks` do ATF não funciona sob Robolectric (o controle negativo não falhou), por isso a verificação é própria. Falta TalkBack real.
- **E2E**: 4 fluxos Maestro (`e2e/flows/01-04`) num emulador (API 34) no CI, contra uma API simulada em Node, sobre o APK minificado.

## 8. Qualidade e CI

ktlint, detekt e Android lint com warnings como erro. CI: lint, testes, Kover, assinatura de release verificada com chave descartável, Trivy no repositório, SBOM CycloneDX varrido, CodeQL, revisão de dependências, envio do grafo do Gradle (o GitHub não o lê sozinho) e E2E no emulador. Actions fixadas por SHA, privilégio mínimo.

## 9. Pendências conhecidas

Chave de upload e secrets do GitHub, ficha de loja, TalkBack em aparelho real, uma única versão de Android no E2E.

## 10. Trade-offs

| Escolha | Ganho | Custo |
|---|---|---|
| DI manual | sem processador de anotações, build rápido | fiação escrita à mão |
| JSON como árvore | sem geração de código | acesso por chave, validado em `JsonAccess` |
| Checker de acessibilidade próprio | roda sem emulador | cobre menos que o ATF |
| E2E sobre release minificado | pega erros de R8 | build mais lento |
| Plataforma única | uso total dos recursos nativos | sem reaproveitamento em iOS |
