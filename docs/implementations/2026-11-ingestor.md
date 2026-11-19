# T2: Ingestor core (snapshot, modelo, parser genérico, agrupamento, dedup, filtro por app)

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/4-ingestor` (issue #4, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T1: Scaffold](2026-11-scaffold.md)
- **Arquivos Principais Modificados**:
  - `app/src/main/kotlin/.../ingest/NotificationSnapshot.kt`, `ingest/Ingestor.kt`
  - `app/src/main/kotlin/.../ingest/parser/NotificationParser.kt` (interface + `ParserRegistry`), `ingest/parser/GenericParser.kt`
  - `app/src/main/kotlin/.../data/Entities.kt`, `data/HistoryDao.kt`, `data/HistoryDatabase.kt`
  - `app/src/test/kotlin/.../ingest/IngestorTest.kt`

---

## 1. O Que Foi Implementado
O módulo Ingestor: recebe `NotificationSnapshot` (sem dependência do Android), aplica filtro por app, escolhe o parser por pacote, agrupa em conversas, deduplica e persiste em Room. Expõe a superfície de consulta (apps, conversas com filtro por app, mensagens de uma conversa). Tabela FTS (`message_fts`) já criada para a busca do T8.

### Contexto de Linhagem / Continuação
- **Estado Anterior**: scaffold com Room/KSP configurado e sem entidades.
- **Delta Desta Etapa**: entidades `app`, `conversation`, `message`, `message_fts`; DAO; Ingestor; 14 testes de comportamento.

## 2. Decisões Tomadas & Justificativas
- **API bloqueante (não `suspend`)**: o listener chama em thread de background; testes ficam simples sem `kotlinx-coroutines-test`. UI futura pode envolver em `withContext(Dispatchers.IO)` ou adicionar consultas `Flow` no DAO.
- **Sem DI**: `Ingestor(database, parsers = ParserRegistry())`.
- **Chave de conversa** = nome da conversa (título do grupo ou do contato) dentro do pacote; único por `(packageName, conversationKey)`. Simples e estável enquanto o contato não muda de nome.
- **Dedup**: mensagem é duplicada se na mesma conversa já existe mesmo `sender` + `text` e mesmo `postedAt`. Só para posts simples (`ParsedMessage.hasOwnTimestamp = false`, `postedAt` = horário do post) a mesma chave de notificação também conta, cobrindo re-post com horário novo. Entradas MessagingStyle têm horário próprio e não casam por chave: mensageiros reusam uma chave por conversa, então "ok" duas vezes em horários diferentes são duas mensagens (revisado na v0.4.0, ver [correções da revisão](2026-11-review-fixes.md)).
- **Apps novos** entram com captura ligada; `setCaptureEnabled` registra o app se ainda não existir.
- **Eventos `REMOVED`** são ignorados por enquanto (detecção de apagadas é T7).
- **`metadata`**: extras do snapshot serializados como linhas `chave=valor`; o T9 deve trocar por `Attachment`.

## 3. Como Foi Implementado
- **Fluxo**: `ingest(snapshot)` (uma transação) → registra/consulta app → se captura desligada retorna `Skipped` → se `REMOVED` ou parser não extrai nada retorna `Ignored` → para cada `ParsedMessage`: busca/cria conversa, checa duplicata, insere mensagem, atualiza `lastActivityAt`, `unreadCount`, título → `Stored(newMessages, duplicates)`.
- **GenericParser**: ignora group summary; com `messages` (MessagingStyle) gera uma mensagem por item, conversa = `conversationTitle` se grupo, senão `title`; sem `messages` gera uma mensagem com conversa = `title` (ou rótulo do app).
- **Esquema**: `message` já tem `deleted`, `read`, `pinned`, `metadata`; índices em `conversationId`, `notificationKey`, `postedAt`. FKs com `CASCADE` (apagar conversa apaga mensagens). `message_fts` é FTS4 com `contentEntity = MessageEntity` (Room mantém triggers de sincronização).
- **Banco**: versão 1, `exportSchema = false`. Ao mudar o esquema, suba a versão e adicione migração.

## 4. Guia para Futuros Agentes
- **Pontos de Extensão**:
  - Parser por app: `ingestor.parsers.register("pacote", parser)` ou `ParserRegistry(parsers = mapOf(...))`.
  - T7 (apagadas/retenção): tratar placeholder dentro de `ingest` antes do dedup, procurando a mensagem original por `notificationKey`; adicionar queries de retenção no `HistoryDao`.
  - T8 (busca/bulk/pin): `JOIN message_fts ON message.id = message_fts.rowid WHERE message_fts MATCH :q`.
  - T10 (backup): ler/escrever pelas entidades; lembrar de manter `message_fts` em sincronia (Room faz via triggers).
- **Gotchas**: chamadas são bloqueantes; nunca na main thread do app. `unreadCount` só é incrementado aqui; marcar como lido (T8) precisa decrementar/zerar. Renomear contato cria nova conversa.
- **Testes**: `app/src/test/kotlin/.../ingest/IngestorTest.kt`, rodar com `./gradlew testDebugUnitTest`.
