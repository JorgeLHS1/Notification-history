# Notification History

🇺🇸 [English](README.md) · 🇧🇷 **Português**

App Android que guarda localmente o histórico de notificações e recupera mensagens apagadas de mensageiros. Gratuito, sem anúncios e **100% offline**: o app não tem permissão de internet.

> Versão atual: **0.4.0**. Testado em Xiaomi (HyperOS 3.0, Android 16). Requer Android 8.0+ (API 26).

## Funcionalidades

- **Histórico de notificações** de qualquer app, agrupado por conversa, com filtro por app e escolha de quais apps capturar.
- **Mensagens apagadas**: WhatsApp e WhatsApp Business. Detecta tanto o aviso "mensagem apagada" quanto a mensagem que some da notificação, e guarda o texto original.
- **Outros mensageiros**: Telegram, Instagram e Messenger, além de captura genérica para qualquer app.
- **Busca** por texto (palavras parciais), filtro "só apagadas", seleção múltipla para marcar como lida/não lida, fixar e apagar.
- **Metadados**: links clicáveis, referências de imagens e ações da notificação.
- **Retenção configurável**: o padrão é 90 dias. As apagadas têm prazo próprio, e as fixadas nunca expiram.
- **Segurança**: banco criptografado (SQLCipher com chave no Android Keystore), bloqueio por biometria ou PIN, e conteúdo oculto na tela de recentes e em capturas de tela.
- **Backup**: exporta e importa um ZIP/JSON numa pasta que você escolhe, sem duplicar mensagens.
- **Mídia recebida** (experimental): copia fotos de uma pasta escolhida por você quando o horário bate com a mensagem.
- Interface em **português (BR)** e **inglês**.

## Instalação no celular

1. Baixe ou gere o APK (veja [Build](#build-e-testes)).
2. No celular, ative **Opções do desenvolvedor → Depuração USB**. Em Xiaomi, ative também **Instalar via USB** (pode pedir chip ativo e login na conta Mi).
3. Instale:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. Abra o app e siga o onboarding:
   - conceda **acesso às notificações**;
   - em Xiaomi/MIUI/HyperOS, ative **Início automático** e tire o app da **economia de bateria**, para o sistema não encerrar a captura.

## Build e testes

Requisitos: Android SDK (plataforma 36) e JDK 17–21. O JDK do Android Studio (`jbr`) funciona; JDKs muito novos (ex.: 25) não são suportados pelo Gradle 8.14.

```bash
./gradlew assembleDebug testDebugUnitTest
```

No Windows, use `gradlew.bat` e aponte `JAVA_HOME` para `C:\Program Files\Android\Android Studio\jbr`. Crie um `local.properties` com `sdk.dir=...` (ele fica fora do git).

> Dica: rode o build fora de pastas sincronizadas pelo OneDrive. A sincronização trava arquivos em `build/` e em `.git/`.

Stack: Kotlin 2.4, Jetpack Compose (Material 3), Room + FTS, SQLCipher, WorkManager, AGP 8.13, Gradle 8.14.

## Estrutura

| Módulo / pasta | Conteúdo |
|---|---|
| `app/` | O app. A regra de negócio fica no **Ingestor** (`ingest/`); o serviço de notificações só repassa os dados. |
| `qa-sender/` | App auxiliar de QA ("NH QA Sender") que simula chats e apaga mensagens, controlável por `adb`. Veja `docs/implementations/2026-11-qa-sender.md`. |
| `docs/implementations/` | O que foi feito em cada parte, decisões e armadilhas. |
| `docs/agents/` | Git Flow, versionamento, testes/QA e rastreador de issues. |
| `AGENTS.md` / `CONTEXT.md` | Regras para agentes de IA e vocabulário do domínio. |

## Fluxo de trabalho

Git Flow: `feat/*` / `fix/*` → `staging` → `develop` → `release/*` → `main`. As branches protegidas não recebem push direto. Versão em SemVer, com CHANGELOG bilíngue. Detalhes em `docs/agents/branching.md` e `docs/agents/versioning.md`.

## Limitações conhecidas

- Só captura mensagens que geraram notificação depois da instalação (não pega conversas silenciadas nem o chat aberto na tela).
- A detecção de "apagada por remoção" compara com a notificação anterior guardada em memória. Se o sistema encerrar o app entre as duas, essa exclusão pode não ser detectada.
- A recuperação de mídia depende de pastas acessíveis pelo seletor do Android. No Android 11+, várias pastas de apps são bloqueadas.
- O backup exportado **não é criptografado**, e as mídias copiadas ficam sem criptografia no armazenamento privado do app.

## Changelog

Veja [CHANGELOG.md](CHANGELOG.md).
