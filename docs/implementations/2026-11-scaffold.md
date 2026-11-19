# T1: Scaffold do projeto Android

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/3-scaffold` (issue #3, spec #1)
- **Continuação de Implementação Anterior?**: Não
- **Documentações Referenciadas**:
  - `CONTEXT.md`, `AGENTS.md`
- **Arquivos Principais Modificados**:
  - `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`
  - `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
  - `app/src/main/kotlin/br/com/lugedevelopment/notificationhistory/MainActivity.kt`
  - `app/src/main/res/values/` e `values-pt-rBR/`
  - `app/src/test/kotlin/.../PlaceholderTest.kt`, `gradlew*`, `gradle/wrapper/*`

---

## 1. O Que Foi Implementado
Projeto Gradle com módulo único `app`: Kotlin, Jetpack Compose (Material 3), Room + KSP, `compileSdk`/`targetSdk` 36, `minSdk` 26, `applicationId` `br.com.lugedevelopment.notificationhistory`, `versionName` 0.1.0 / `versionCode` 1. Tela inicial Compose vazia (título + texto de estado vazio). Strings em `values` (en) e `values-pt-rBR`. Teste JVM placeholder com Robolectric. Gradle wrapper commitado.

---

## 2. Decisões Tomadas & Justificativas
- **Gradle 8.14.5 + AGP 8.13.2**: AGP 8.13 é a última 8.x e suporta compileSdk 36. AGP 9.x foi evitado (Kotlin embutido muda a configuração).
- **Bibliotecas AndroidX fixadas em versões compatíveis com compileSdk 36**: as versões mais novas (core-ktx 1.19, lifecycle 2.11, activity 1.13, Compose BOM 2026.11) exigem compileSdk 37 e AGP 9.1+. Ao subir compileSdk/AGP, atualize todas juntas.
- **Robolectric** em vez de apenas Room em memória: cobre ambos (Room testing disponível) sem precisar de emulador.
- **Sem permissão de rede** e `allowBackup=false` (regra 6 do AGENTS.md).
- **Ícone adaptativo vetorial** simples e original (sino), sem assets de terceiros.

## 3. Como Foi Implementado
- Versões centralizadas em `gradle/libs.versions.toml` (version catalog).
- Código-fonte em `src/main/kotlin` e `src/test/kotlin` (sourceSets declarados).
- `unitTests.isIncludeAndroidResources = true` para o Robolectric.
- Room ainda sem entidades; KSP já configurado (`ksp(room-compiler)`).

## 4. Guia para Futuros Agentes
- **Pontos de Extensão**: pacotes de domínio/Ingestor/Room sob `br.com.lugedevelopment.notificationhistory`; novas strings sempre em `values` e `values-pt-rBR`.
- **Gotchas**: use JDK do Android Studio (JBR 21) como `JAVA_HOME`, não o Java 25 do sistema. `local.properties` é ignorado pelo git; crie com `sdk.dir`. Primeira build baixa dependências (~3 min).
- **Testes**: `./gradlew assembleDebug testDebugUnitTest` (`PlaceholderTest`).
