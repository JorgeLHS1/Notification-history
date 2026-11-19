# v0.4.0: Correções da revisão de código (dedup, apagadas, mídia, UI, segurança)

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de correções)
- **Branch / PR**: `feat/1-notification-history` (spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T2: Ingestor core](2026-11-ingestor.md)
  - [T3: Listener](2026-11-listener.md)
  - [T4: Segurança](2026-11-security.md)
  - [T5: UI](2026-11-ui.md)
  - [T7: Apagadas e retenção](2026-11-deleted-retention.md)
  - [T9: Parsers e metadados](2026-11-parsers-metadata.md)
  - [T10: Mídia e backup](2026-11-media-backup.md)
- **Arquivos Principais Modificados**:
  - `ingest/Ingestor.kt`, `ingest/parser/NotificationParser.kt`, `ingest/parser/GenericParser.kt`, `data/HistoryDao.kt`
  - `listener/SnapshotMapper.kt`, `listener/HistoryListenerService.kt`
  - `media/MediaRecovery.kt`, `NotificationHistoryApp.kt`, `MainActivity.kt`, `security/SecureGate.kt`
  - `ui/NotificationHistoryApp.kt`, `ui/BackupMediaSection.kt`
  - Testes: `ingest/ReviewFixesTest.kt` (novo), `listener/SnapshotMapperTest.kt`, `ingest/DeletedRetentionTest.kt`

---

## 1. O que foi implementado

### Contexto de linhagem
- **Estado anterior**: T2 a T10 entregues; a revisão de código apontou 9 problemas.
- **Delta desta etapa**: corrige os 9 sem mudar o esquema (DB segue na versão 1).

| # | Problema | Correção |
|---|---|---|
| 1 | Dedup por chave descartava "ok" repetido na mesma conversa (mensageiros reusam a chave) | `ParsedMessage.hasOwnTimestamp`; MessagingStyle deduplica só por `postedAt`; chave só para posts simples. Import de backup usa só `postedAt`. |
| 2 | `?.let { put(PICTURE, ...) }` estava dentro de um comentário em `SnapshotMapper` | Expressão corrigida; URI (String ou Uri) ou marcador `embedded`. |
| 3 | Fallback por chave em `deletionCandidate` marcava a mensagem mais nova | `deletionCandidateAt` (mesmo horário) e, senão, `deletionCandidatesByKey` só se houver exatamente uma candidata. |
| 4 | Placeholder criava conversa vazia | Conversa só é criada ao gravar mensagem. |
| 5 | Mídia copiada nunca era apagada | `Ingestor(deleteMediaFile)` chamado após o commit em `applyRetention` e `deleteMessages`; app liga em `PrivateMediaStore.delete`. |
| 6 | Polling a cada 2 s + N+1 de última mensagem | `Ingestor.changes()` (InvalidationTracker) + `HistoryDao.lastMessages()` numa query. |
| 7 | Backup/importação/varredura bloqueavam a captura | `NotificationHistoryApp.taskExecutor` separado. |
| 8 | `SessionLock` em `remember` pedia biometria de novo na rotação | `NotificationHistoryApp.sessionLock` (escopo do processo). |
| 9 | IPC ao `PackageManager` por notificação | Cache de rótulos por pacote no serviço. |

## 2. Decisões tomadas

- **Flag no `ParsedMessage` em vez de heurística no DAO**: só o parser sabe se o horário é da mensagem ou do post. Padrão `false` mantém parsers de terceiros no comportamento antigo (chave conta).
- **Fallback de apagada restrito a candidata única**: preferimos não marcar nada a marcar a mensagem errada. Trade-off: post simples com várias mensagens sob a mesma chave e placeholder com horário novo não é detectado.
- **Exclusão de arquivo fora da transação**: os caminhos são coletados dentro da transação e apagados depois do commit; se o commit falhar, nenhum arquivo some. Falha em um arquivo não interrompe os demais.
- **`PrivateMediaStore.delete` recusa caminhos fora de `filesDir/media`**: um backup importado pode trazer qualquer caminho em `MEDIA_FILE`; sem essa checagem, apagar a mensagem poderia apagar o banco.
- **Mídia sem criptografia**: as cópias ficam em texto claro no armazenamento privado do app. Criptografar exigiria stream cifrado e chave própria; adiado.
- **`changes()` como Flow conflated em `Dispatchers.IO`**: registrar o observer pode abrir o banco (SQLCipher deriva a chave), então nunca na main thread; rajadas de gravação viram um único reload.
- **Lock no `Application`**: mais simples que ViewModel e sobrevive a qualquer recriação da activity; o timeout de 30 s continua valendo ao voltar do segundo plano.

## 3. Como funciona

- **Ingest**: `existing = dao.conversation(...)`; placeholder → `deletionTarget` só se `existing != null`; dedup → `countDuplicates(..., matchKey = !hasOwnTimestamp)`; só então cria a conversa.
- **Mídia**: `expiringMediaFiles(cutoff)` / `expiringDeletedMediaFiles(cutoff)` / `mediaFilesOf(ids)` espelham os filtros dos `DELETE` correspondentes.
- **UI**: `RefreshOnChange(ingestor) { vm.load() }` em cada destino; `repeatOnLifecycle(STARTED)` + `ingestor.changes().collect`.
- **Listener**: `labels: ConcurrentHashMap<String, String>`, limpo em `onListenerConnected`.

## 4. Guia para futuros agentes

- Parser novo que lê MessagingStyle: marque `hasOwnTimestamp = true` nas entradas com horário próprio.
- Nova forma de remover mensagens: colete os `MEDIA_FILE` antes do `DELETE` e chame `removeMediaFiles` após a transação.
- Tabela nova que a UI mostra: inclua o nome em `Ingestor.changes()`.
- Gotcha: o observer do Room é registrado de forma assíncrona; em teste, grave até ver a emissão (ver `ReviewFixesTest.changesEmitsInitiallyAndAfterAWrite`).

## 5. QA manual

1. Receber "ok" duas vezes do mesmo contato no WhatsApp com 1 min de intervalo: a conversa mostra as duas.
2. Apagar uma mensagem no remetente: só ela fica marcada como apagada; apagar algo nunca capturado não cria conversa vazia.
3. Notificação com foto (BigPicture): anexo de imagem aparece na mensagem.
4. Recuperar mídia, apagar a mensagem pela seleção: o arquivo some de `files/media` (`adb shell run-as br.com.lugedevelopment.notificationhistory ls files/media`).
5. Com a lista aberta, chegar notificação: a tela atualiza sem atraso; parada, não há recarga contínua. Exportar backup grande enquanto chegam notificações: captura continua. Girar a tela desbloqueado: não pede biometria.

## Device fix: `formatTime` crash on Android 16

- **Symptom:** opening the app with captured conversations crashed (`NullPointerException ... Context.getUserId()` in `ui/Screens.kt` `formatTime`).
- **Cause:** `DateUtils.formatDateTime(null, ...)` needs a real `Context` on device; Robolectric tolerated `null`, so unit tests passed.
- **Fix:** `formatTime` now uses `java.text.DateFormat` (short time today, short date otherwise, device locale). No `Context` needed.
- **Gotcha for future agents:** never pass `null` as `Context` to framework helpers; Robolectric hides it. Verified on the Xiaomi 2311DRK48G (Android 16).
