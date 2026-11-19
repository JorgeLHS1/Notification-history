# T9: Parsers de Telegram, Instagram e Messenger + metadados (Attachments)

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/11-parsers` (issue #11, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T2: Ingestor core](2026-11-ingestor.md)
  - [T3: Listener](2026-11-listener.md)
  - [T5: UI](2026-11-ui.md)
  - [T7: Apagadas e retenção (padrão do parser WhatsApp)](2026-11-deleted-retention.md)
- **Arquivos Principais Modificados**:
  - `ingest/parser/MessengerParsers.kt` (novo: `ChatAppParser`, `TelegramParser`, `InstagramParser`, `MessengerParser`), `ingest/parser/NotificationParser.kt` (registro padrão)
  - `ingest/Attachments.kt` (novo: `AttachmentExtras`, `LinkExtractor`, `AttachmentBuilder`)
  - `data/Entities.kt` (`AttachmentEntity`, `AttachmentType`), `data/HistoryDao.kt`, `data/HistoryDatabase.kt` (DB segue na versão 1)
  - `ingest/Ingestor.kt` (popula e consulta anexos), `listener/SnapshotMapper.kt` (extras de imagem, ícone e ações), `ui/Screens.kt` (links tocáveis)
  - `app/src/test/kotlin/.../ingest/MessengerParsersTest.kt`

---

## 1. O que foi implementado

- Parsers registrados por padrão: Telegram (`org.telegram.messenger`, `.web`), Instagram (`com.instagram.android`) e Messenger (`com.facebook.orca`).
- Entidade `Attachment` (tipos `LINK`, `IMAGE`, `LARGE_ICON`, `ACTION`) ligada à mensagem com cascade; o Ingestor a preenche a partir do texto e dos extras do snapshot.
- Links tocáveis no balão de mensagem (`LinkAnnotation.Url`, aberto pelo sistema; o app continua sem permissão de rede).

## 2. Decisões tomadas

- **Base comum `ChatAppParser`** (sobre o `GenericParser`): remove contadores "(3 messages)" dos títulos, descarta resumos ("5 new messages", título = rótulo do app) e posts sem conteúdo.
- **Telegram**: posts simples de grupo usam título "Remetente @ Grupo" e viram conversa de grupo. Instagram e Messenger: grupos vêm em MessagingStyle (caminho genérico); posts simples são tratados como 1:1.
- **Anexos de texto (links)** são extraídos de cada mensagem nova; **anexos de notificação** (imagem, ícone grande, ações) vão para a mensagem nova mais recente do snapshot. Duplicadas não geram anexos.
- **Só referências, nunca pixels**: imagem/ícone guardam a URI se o app a fornece, senão o marcador `embedded`. Ações guardam o rótulo do botão.
- `EXTRA_PICTURE_URI` exige API 31; usada a chave literal `android.pictureUri` (minSdk 26), lida como String ou Uri. Sem URI, `EXTRA_PICTURE`/`android.pictureIcon` viram o marcador `embedded`. (Até a v0.4.0 a expressão estava dentro de um comentário e a URI era perdida; ver [correções da revisão](2026-11-review-fixes.md).)
- Fixtures sintéticas; formatos de título são heurísticas a confirmar em aparelho.

## 3. Guia para futuros agentes

- Novo app de mensagens: subclasse de `ChatAppParser` e registro em `ParserRegistry.defaultParsers()`.
- Novas chaves de extras: constantes em `AttachmentExtras` + `AttachmentBuilder.fromExtras`.
- Exibir imagens (T10): ler `Ingestor.attachments(...)`; `embedded` significa que o bitmap não foi persistido (guardar bytes exige decisão nova de armazenamento).
- Gotcha: `LinkExtractor` é compartilhado com a UI; mudar a regex afeta armazenamento e exibição.

## 4. QA manual

1. Mensagem 1:1 e de grupo em Telegram, Instagram e Messenger: uma conversa por contato/grupo, mesmo com contador no título.
2. Mensagem com link: aparece sublinhado e abre no navegador ao tocar; pontuação final fora do link.
3. Notificação com foto: anexo `IMAGE` registrado (conferir via `Ingestor.attachments`).
4. Borda: notificações de resumo e atividade sem texto não criam mensagens.
