# T6: Onboarding (acesso às notificações + guia Xiaomi) e card de status

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/8-onboarding` (issue #8, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T3: Listener](2026-11-listener.md) (`ListenerStatus`)
  - [T5: UI](2026-11-ui.md)
  - [T4: Segurança](2026-11-security.md) (`SecureGate`)
- **Arquivos Principais Modificados**:
  - `app/src/main/kotlin/.../onboarding/OnboardingLogic.kt`, `OnboardingScreen.kt`, `OnboardingGate.kt` (novos)
  - `app/src/main/kotlin/.../MainActivity.kt` (uma camada `OnboardingGate { ... }` ao redor da UI)
  - `app/src/main/res/values/onboarding_strings.xml`, `values-pt-rBR/onboarding_strings.xml` (novos)
  - `app/src/test/kotlin/.../onboarding/OnboardingLogicTest.kt`

---

## 1. O Que Foi Implementado
- Onboarding de primeira execução: explica por que o acesso às notificações é necessário (e que tudo fica offline), abre a tela do sistema e, com o acesso concedido, mostra um guia de execução em segundo plano.
- Detecção de acesso revogado: se o acesso some depois de o onboarding ter sido concluído, o app volta para a etapa de acesso com texto específico de "revogado".
- Guia MIUI/HyperOS (Xiaomi, Redmi, POCO) com deep links para Inicialização automática e economia de bateria; em outros fabricantes aparece apenas o texto genérico e o botão de otimização de bateria padrão do Android.
- Card de status (acesso concedido / listener conectado / otimização de bateria ignorada) exibido acima da UI principal enquanto algo estiver pendente, com botão para reabrir o guia.

## 2. Decisões Tomadas & Justificativas
- **Um único gancho**: `OnboardingGate` envolve `HistoryUi` em `MainActivity` (dentro de `SecureGate`). Nada em `ui/` foi alterado, evitando conflito com T8/T9.
- **Roteamento puro e testável**: `nextStep(accessGranted, completed, guideRequested)`. Acesso ausente sempre vence; o guia roda uma vez (flag `onboarding_completed` em SharedPreferences `onboarding_settings`) ou sob demanda.
- **Reavaliação no ON_RESUME** (usuário volta das configurações do sistema) para acesso e bateria; `ListenerStatus.connected` é coletado como StateFlow.
- **Bateria**: `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (lista do sistema) em vez de `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, para não declarar a permissão `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
- **Falha graciosa**: `launchFirst(candidatos, fallback, start)` tenta cada intent do fabricante dentro de try/catch (`ActivityNotFoundException`, `SecurityException`), depois a tela de detalhes do app, e por fim um Toast. Sem `<queries>` nem `resolveActivity`, que não enxergam componentes de outros pacotes no Android 11+.
- **Dica de "configurações restritas"**: em APKs instalados fora de loja, o Android 13+ bloqueia a chave de acesso às notificações até o usuário permitir "configurações restritas" na tela de informações do app; há texto e botão para isso.
- Strings em `onboarding_strings.xml` (en e pt-BR), separado de `strings.xml`.

## 3. Como Foi Implementado
- `SettingsIntents`: `xiaomiAutostart()` (componente do centro de segurança + action de fallback), `xiaomiBatterySaver(pkg, label)` (componente do gerenciador de energia + action), `batteryOptimizationList()`, `appDetails(pkg)`.
- `OemDetector.isXiaomiFamily(manufacturer, brand)` compara com xiaomi/redmi/poco.
- `OnboardingGate`: sem acesso ou guia pendente mostra `OnboardingScreen`; caso contrário mostra `StatusCard` (se `!allOk`) + conteúdo, consumindo o inset da status bar para não duplicar o padding do Scaffold.

## 4. Verificação
- `assembleDebug` e `testDebugUnitTest` passam (10 testes novos: roteamento, revogação, detecção de OEM, cadeia de fallback, flag persistida).
- Dispositivo Xiaomi (HyperOS 3.0, Android 16) via adb: o componente de Inicialização automática abre; o componente `HiddenAppsConfigActivity` **não existe** nesta versão (`Activity class ... does not exist`), então o fluxo cai na tela de detalhes do app (testada). A instalação do APK via adb foi recusada pelo aparelho (`INSTALL_FAILED_USER_RESTRICTED`), portanto o fluxo de telas não foi exercitado no dispositivo.

## 5. Gotchas & Pontos de Extensão
- Componentes de fabricante mudam entre versões do ROM; para suportar outra versão, acrescente candidatos nas listas de `SettingsIntents` (o primeiro que abrir vence).
- `ListenerStatus.connected` é local do processo e começa `false`; o card mostra "Pendente" por instantes após abrir o app.
- Outros fabricantes (ex.: guias próprios de autostart) podem ser adicionados com um `OemDetector` e novos botões em `GuideStep`.

## 6. Roteiro de QA
1. Instalação limpa: abrir o app mostra a etapa de acesso; botão abre a tela de acesso às notificações.
2. Ativar o acesso e voltar: aparece o guia com o card de status; "Concluir" leva ao histórico.
3. Em Xiaomi: os botões de Inicialização automática e economia de bateria abrem a tela ou a de detalhes do app, sem crash.
4. Desativar o acesso nas configurações e voltar: aparece a etapa com texto de acesso revogado.
5. Em fabricante não Xiaomi: nenhum botão Xiaomi aparece.
6. Com algo pendente após o onboarding: card no topo da lista; "Abrir guia de configuração" reabre o guia.