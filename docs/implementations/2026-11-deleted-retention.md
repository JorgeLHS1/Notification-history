# T7: Parser WhatsApp/Business, detecção de apagadas e retenção

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/9-deleted` (issue #9, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T1: Scaffold](2026-11-scaffold.md)
  - [T2: Ingestor core](2026-11-ingestor.md)
- **Arquivos Principais Modificados**:
  - `ingest/parser/WhatsAppParser.kt` (novo), `ingest/parser/NotificationParser.kt` (`ParserRegistry.defaultParsers()`)
  - `ingest/Ingestor.kt` (detecção de apagadas, `applyRetention`, `setPinned`, `RetentionPolicy`, `RetentionResult`, `DeletedPlaceholders`)
  - `data/Entities.kt` (`MessageEntity.deletedAt`), `data/HistoryDao.kt` (queries de apagadas/retenção)
  - `retention/RetentionWorker.kt` (novo), `gradle/libs.versions.toml` + `app/build.gradle.kts` (WorkManager 2.11.0)
  - `app/src/test/kotlin/.../ingest/DeletedRetentionTest.kt`

---

## 1. O que foi implementado

- Parser para `com.whatsapp` e `com.whatsapp.w4b`, registrado por padrão no `ParserRegistry`.
- Detecção de mensagens apagadas: quando uma atualização da notificação traz o placeholder localizado de "mensagem apagada", a mensagem original é marcada `deleted = true` (com `deletedAt`) e o texto original é preservado. O placeholder não é armazenado.
- Retenção separada: geral (padrão 90 dias, por `postedAt`) e apagadas (padrão 365 dias, por `deletedAt`). Fixadas nunca expiram. Conversas que ficam vazias são removidas.
- `RetentionWorker` (WorkManager, periódico diário) que apenas chama `Ingestor.applyRetention`.

## 2. Decisões tomadas

- **Placeholder detectado no Ingestor, não no parser**: vale para qualquer app que use o mesmo padrão. Lista em `DeletedPlaceholders` (EN, PT-BR, PT-PT, ES); a normalização remove ícones iniciais, ponto final e caixa.
- **Casamento do original**: mesma conversa + mesmo remetente + (mesmo `postedAt` OU mesma chave de notificação), preferindo o mesmo `postedAt` (o app mantém o timestamp original na entrada substituída). Sem original capturado, o placeholder é descartado.
- **Eventos REMOVED continuam ignorados**: remoção de notificação não distingue "apagada pelo remetente" de "dispensada/lida"; só o placeholder é sinal confiável.
- **Retenção de apagadas conta a partir de `deletedAt`** (coluna nova; DB segue na versão 1, sem migração, pois não há instalações).
- **Política como parâmetro**: `notes/settings-keys.md` (T5) não existia; a política entra como `RetentionPolicy` no Ingestor e como input data (`generalDays`, `deletedDays`; `<= 0` = manter para sempre) no worker, com defaults do `RetentionPolicy`.

## 3. Como foi implementado

- `Ingestor.ingest`: para cada `ParsedMessage`, se `DeletedPlaceholders.matches(text)` → `dao.deletionCandidate(...)` → `dao.markDeleted(id, snapshot.postedAt)`; `IngestResult.Stored` ganhou `markedDeleted`.
- `Ingestor.applyRetention(policy, now)`: uma transação com `deleteExpiredMessages`, `deleteExpiredDeletedMessages` e `deleteEmptyConversations`. O índice FTS é mantido por triggers do Room.
- `WhatsAppParser`: remove sufixo contador ("(3 messages)", "(3 mensagens)") de títulos, separa "Grupo: Remetente" em posts sem MessagingStyle, ignora resumos do app (título = rótulo do app); o resto delega ao `GenericParser`.
- `RetentionWorker.schedule(context, policy)`: `enqueueUniquePeriodicWork("retention-cleanup", UPDATE, 1 dia)`.

## 4. Guia para futuros agentes

- **Quem agenda o worker**: o Application (T3) deve chamar `RetentionWorker.schedule(context)` na inicialização; a UI (T5) chama de novo com a política escolhida quando o usuário mudar a retenção.
- O worker usa `HistoryDatabase.build(context)`; se T4 (DB criptografado) mudar a forma de abrir o banco, ajuste só essa linha.
- Novo idioma de placeholder: adicione a string normalizada (minúsculas, sem ícone/ponto) em `DeletedPlaceholders`.
- `Ingestor.setPinned` foi adicionado para os testes de retenção; a UI pode reutilizá-lo.
- Fixtures de teste são sintéticas (sem nomes ou textos reais).

## 5. QA manual

1. Receber mensagem 1:1 e de grupo no WhatsApp e no WhatsApp Business; conferir conversa única por grupo mesmo com contador no título.
2. Remetente apaga a mensagem (PT e EN): item fica marcado como apagado com o texto original.
3. Dispensar a notificação: nada é marcado como apagado.
4. Borda: placeholder de mensagem nunca capturada não aparece no histórico.
