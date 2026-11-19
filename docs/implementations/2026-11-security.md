# T4: Segurança (SQLCipher, chave no Keystore, gate biométrico, FLAG_SECURE)

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/6-security` (issue #6, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T1: Scaffold](2026-11-scaffold.md)
  - [T2: Ingestor](2026-11-ingestor.md)
- **Arquivos Principais Modificados**:
  - `app/src/main/kotlin/.../security/DatabaseKey.kt`, `EncryptedDatabase.kt`, `SessionLock.kt`, `SecureGate.kt`
  - `app/src/main/kotlin/.../MainActivity.kt` (edição mínima)
  - `app/src/main/res/values{,-pt-rBR}/security_strings.xml`
  - `app/src/test/kotlin/.../security/SecurityTest.kt`
  - `gradle/libs.versions.toml`, `app/build.gradle.kts` (SQLCipher 4.6.1, androidx.biometric 1.1.0)

---

## 1. O Que Foi Implementado
- **Banco criptografado**: `EncryptedDatabase.build(context)` devolve um `HistoryDatabase` com SQLCipher (`SupportOpenHelperFactory`). Arquivo `history-secure.db`.
- **Chave**: 32 bytes aleatórios gerados uma vez, guardados apenas **encapsulados** (AES-256-GCM com chave não exportável do Android Keystore) em `filesDir/history-db.key`.
- **Gate de autenticação**: composable `SecureGate { ... }` exige biometria ou credencial do aparelho na abertura e ao voltar do segundo plano após 30 s (`SessionLock`).
- **FLAG_SECURE**: `FragmentActivity.applySecureWindow()` bloqueia captura de tela e miniatura em recentes.

## 2. Decisões Tomadas & Justificativas
- **`BIOMETRIC_WEAK | DEVICE_CREDENTIAL`**: única combinação aceita desde a API 26 (STRONG + credencial só a partir da API 30).
- **Falha fechada**: sem bloqueio de tela/biometria cadastrado, o conteúdo permanece oculto e uma mensagem orienta o usuário.
- **`MainActivity` agora é `FragmentActivity`** (exigido pelo `BiometricPrompt`; continua sendo `ComponentActivity`).
- **Arquivo novo `history-secure.db`**: não colide com o `history.db` não criptografado de `HistoryDatabase.build`. Esquema segue na versão 1; `HistoryDatabase.kt` não foi alterado.
- **Lógica de bloqueio pura** (`SessionLock`, relógio injetável) e `KeyWrapper` como interface, para testar na JVM sem Keystore/biometria.
- **Strings em arquivos próprios** (`security_strings.xml`) para evitar conflitos de merge com a UI.

## 3. Como Foi Implementado
- `DatabaseKeyStore.loadOrCreate()`: se o arquivo existe, `unwrap`; senão gera, `wrap` e grava.
- `KeystoreKeyWrapper`: saída `iv(12) || ciphertext+tag`; chave criada sob demanda com o alias `notification_history_db_wrap_key`.
- `SecureGate` observa o ciclo de vida: `ON_STOP` → `SessionLock.onBackgrounded()`; `ON_START` → `onForegrounded()` e, se bloqueado, abre o `BiometricPrompt`.
- O `SessionLock` é do processo (`NotificationHistoryApp.sessionLock`, passado por `MainActivity`), não um `remember`: rotação não pede autenticação de novo (revisado na v0.4.0, ver [correções da revisão](2026-11-review-fixes.md)).

## 4. Guia para Futuros Agentes
- **Obter o banco**: `EncryptedDatabase.build(applicationContext)` uma única vez (ex.: em `Application`) e compartilhar com Ingestor/UI. Testes: `HistoryDatabase.inMemory(ctx)` (sem criptografia).
- **Proteger a UI**: envolver o conteúdo raiz em `SecureGate { ... }` dentro de uma `FragmentActivity` que chame `applySecureWindow()` antes de `setContent`. Outras activities devem fazer o mesmo.
- **Gotchas**:
  - O serviço de notificações precisa gravar com o app bloqueado: o gate protege só a UI, a chave vem do Keystore sem autenticação do usuário.
  - Limpar dados do app apaga o arquivo da chave; o Keystore pode manter a chave antiga, o que é inofensivo (nova chave é gerada).
  - Perder o `history-db.key` torna o banco ilegível (sem recuperação). Backup (T10) deve exportar dados em claro via SAF por escolha do usuário, nunca o arquivo `.db`.
  - As libs nativas do SQLCipher não carregam no Robolectric; não use `EncryptedDatabase` em testes unitários.
  - Se mudar o prazo de bloqueio, ajuste `SessionLock.DEFAULT_TIMEOUT_MS` (futuro: preferência em T11).
- **Verificação manual (QA)**: abrir o app (deve pedir biometria/PIN); ir para segundo plano por mais de 30 s e voltar (pede de novo); tentar screenshot e olhar recentes (bloqueados); remover bloqueio de tela do aparelho (conteúdo fica oculto com aviso); inspecionar `history-secure.db` via adb (ilegível sem a chave).
- **Testes**: `SecurityTest.kt` cobre geração/estabilidade da chave encapsulada e as regras de bloqueio/timeout. `./gradlew testDebugUnitTest`.
