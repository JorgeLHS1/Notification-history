# T3: Listener service, boot handling and access status

- **Data**: 2026-11-19
- **Autor / Agente**: Subagente T3
- **Branch / PR**: `feat/5-listener` (issue #5, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [Scaffold](file:///docs/implementations/2026-11-scaffold.md)
  - [Ingestor](file:///docs/implementations/2026-11-ingestor.md)
- **Arquivos Principais Modificados**:
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/kotlin/br/com/lugedevelopment/notificationhistory/NotificationHistoryApp.kt`
  - `app/src/main/kotlin/br/com/lugedevelopment/notificationhistory/listener/HistoryListenerService.kt`
  - `app/src/main/kotlin/br/com/lugedevelopment/notificationhistory/listener/SnapshotMapper.kt`
  - `app/src/main/kotlin/br/com/lugedevelopment/notificationhistory/listener/ListenerStatus.kt`
  - `app/src/test/kotlin/br/com/lugedevelopment/notificationhistory/listener/SnapshotMapperTest.kt`

---

## 1. O Que Foi Implementado
Serviço `NotificationListenerService` fino que converte cada notificação postada/removida em `NotificationSnapshot` e a entrega ao `Ingestor`, declaração no manifest, API pública de estado (acesso concedido / serviço conectado) e uma `Application` que é o único ponto que constrói o `Ingestor`.

### Contexto de Linhagem / Continuação
- **Estado Anterior**: o Ingestor (T2) existia, sem nenhuma fonte de dados real.
- **Delta Desta Etapa**: fonte de dados (listener), composição do app e status para a UI. Schema do banco (versão 1) inalterado.

---

## 2. Decisões Tomadas & Justificativas
- **Sem BroadcastReceiver de boot**: o sistema religa listeners habilitados após reboot sozinho; um receiver exigiria `RECEIVE_BOOT_COMPLETED` sem ganho. Em `onListenerDisconnected` chamamos `requestRebind` como reforço.
- **Application como composition root**: `NotificationHistoryApp.ingestor` (lazy) e `ioExecutor` (thread única, preserva ordem de chegada). Serviço, UI e workers reutilizam; ninguém cria outro Ingestor.
- **Mapeamento na thread do binder, ingestão fora dela**: o Room é bloqueante; o `sbn` é lido antes de enfileirar.
- **Nenhuma regra no serviço** (AGENTS.md regra 7): nem filtro por app, nem descarte de resumos de grupo (campo `isGroupSummary` é só copiado). Erros são engolidos com `runCatching` para nunca derrubar o serviço.
- **Rótulo do app**: via `PackageManager`; se o pacote não for visível (Android 11+ sem visibilidade), cai para o nome do pacote.
- Trade-off: `connected` é local ao processo e volta a `false` quando o processo morre.

---

## 3. Como Foi Implementado
- **Fluxo**: sistema -> `HistoryListenerService.onNotificationPosted/Removed` -> `SnapshotMapper.map` (cópia de campos: título, texto, MessagingStyle, flags, extras `bigText`/`subText`) -> `ioExecutor` -> `Ingestor.ingest`.
- **Contratos**: `ListenerStatus.isAccessGranted(ctx)`, `ListenerStatus.connected: StateFlow<Boolean>`, `ListenerStatus.accessSettingsIntent()`. Uso detalhado no handoff para a UI.
- **Manifest**: `<service>` com `BIND_NOTIFICATION_LISTENER_SERVICE` e a action `android.service.notification.NotificationListenerService`; `android:name` da application. Sem permissão de rede.

---

## 4. Guia para Futuros Agentes
- **Pontos de Extensão**: trocar o construtor do banco (SQLCipher/biometria) apenas na linha `ingestor` da Application; novos campos do snapshot entram em `SnapshotMapper`.
- **Gotchas**: o banco só abre no primeiro uso do `ingestor` (lazy); se o banco ficar bloqueado por biometria, a ingestão precisa tratar isso no Ingestor/DB, não no serviço. Removidas chegam como `REMOVED` e o Ingestor hoje as ignora (T7).
- **Testes**: `SnapshotMapperTest` (Robolectric). `.\gradlew.bat testDebugUnitTest`.

## 5. Checagem manual em aparelho (pendente de execução humana)
1. Instalar o debug e abrir o app; em Configurações > Acesso a notificações, ativar o app.
2. Enviar uma notificação de qualquer app; confirmar que aparece no histórico.
3. Reiniciar o aparelho, sem abrir o app; enviar outra notificação e confirmar que foi capturada.
4. Desativar o acesso; `isAccessGranted` deve ser falso e nada novo ser gravado.
5. Borda: notificação de grupo (várias mensagens), app com captura desligada, aparelho com bateria otimizada.
