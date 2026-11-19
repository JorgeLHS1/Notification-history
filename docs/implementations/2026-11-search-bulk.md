# T8: Busca, seleção múltipla, lida/não lida, excluir e fixar

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/10-search` (issue #10, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T2: Ingestor](2026-11-ingestor.md)
  - [T5: UI](2026-11-ui.md)
  - [T7: Apagadas e retenção](2026-11-deleted-retention.md)
- **Arquivos Principais Modificados**:
  - `app/src/main/kotlin/.../data/HistoryDao.kt` (consultas de busca e operações em lote)
  - `app/src/main/kotlin/.../ingest/Ingestor.kt`, `ingest/SearchText.kt`
  - `app/src/main/kotlin/.../ui/Screens.kt`, `ui/SearchUi.kt`, `ui/ViewModels.kt`, `ui/NotificationHistoryApp.kt`
  - `app/src/main/res/values/search_strings.xml`, `values-pt-rBR/search_strings.xml`
  - Testes: `ingest/SearchBulkTest.kt`, `ui/SearchViewModelsTest.kt`

---

## 1. O Que Foi Implementado
Busca de texto completo sobre o histórico, filtro "só apagadas", seleção múltipla com ações em lote (marcar como lida/não lida, fixar/desafixar, excluir) e a UI correspondente: barra de busca com resultados destacados que abrem a conversa já posicionada na mensagem.

- **Estado Anterior**: Ingestor sem busca; só `setPinned(id)` por mensagem; tabela `message_fts` (FTS4) existia mas não era consultada.
- **Delta Desta Etapa**: API de consulta/lote no Ingestor, UI de busca e seleção. Schema do banco continua na versão 1 (nenhuma coluna ou tabela nova).

---

## 2. Decisões Tomadas & Justificativas
- **Prefixo em todas as palavras**: a consulta vira `termo1* termo2*` (AND implícito), então palavras parciais funcionam. Só letras/dígitos entram na expressão; qualquer outro caractere vira separador, o que elimina erro de sintaxe FTS com entrada do usuário (aspas, parênteses, `*`, `-`).
- **Consulta em branco**: retorna vazio; exceção: com `deletedOnly = true` lista todas as apagadas.
- **Tokenizer padrão (simple)**: insensível a maiúsculas só para ASCII e sem dobrar acentos. Trocar para `unicode61` mudaria o schema (identity hash) e exigiria migração; fica como melhoria futura.
- **Contador de não lidas**: após qualquer operação em lote, `conversation.unreadCount` é recalculado a partir das mensagens com `read = 0` (fonte única de verdade), e a ingestão continua incrementando. Mensagens novas entram como não lidas.
- **Excluir remove também fixadas**: o usuário escolheu explicitamente; a regra "fixadas nunca expiram" vale só para a retenção automática. Conversas que ficam vazias são removidas.
- **Lotes em blocos de 500 ids** dentro de uma transação, por causa do limite de variáveis do SQLite.
- **Limite de resultados**: 200 por busca (`Ingestor.SEARCH_LIMIT`).
- **Destaque na UI**: `SearchText.highlightRanges` (puro e testado) calcula os trechos; a UI só pinta.
- **Seleção**: toque longo inicia; toque simples alterna enquanto houver seleção; Voltar limpa a seleção. Barra de ações inferior; excluir pede confirmação.

---

## 3. Como Foi Implementado
- **Ingestor**: `search(query, deletedOnly, packages, limit): List<SearchHit(message, conversation)>`, `setRead(ids, read)`, `setPinned(ids, pinned)` (sobrecarga em lote), `deleteMessages(ids): Int`.
- **DAO**: `search` (JOIN `message_fts` por `rowid`, filtros de apagadas e de apps), `deletedMessages`, `setRead`, `setPinned`, `deleteMessages`, `conversationIdsOf`, `recountUnread`, `conversationById`.
- **ViewModels**: `HomeState` ganha `searchOpen`, `query`, `deletedOnly`, `hits`, `selection`; `ConversationState` ganha `selection`. Ambos expõem `toggleSelected`, `clearSelection`, `markSelectedRead`, `pinSelected`, `deleteSelected`.
- **Navegação**: a raiz guarda `focusMessageId` e `highlightQuery`; `ConversationScreen` rola até a mensagem uma vez, com borda de destaque e termos pintados. O estado da busca fica no ViewModel, então voltar da conversa reabre os mesmos resultados.
- **Strings**: `search_strings.xml` em `values` e `values-pt-rBR`.

---

## 4. Guia para Futuros Agentes
- **Pontos de Extensão**: filtros extras entram como parâmetros de `Ingestor.search` e cláusulas no DAO; novas ações em lote seguem o padrão `bulk { ids -> ... }` (recalcula não lidas e remove conversas vazias).
- **Gotchas**: `Ingestor.setPinned` tem duas sobrecargas (id único e coleção). Ids inexistentes são ignorados sem erro. A tela de conversa não marca como lida ao abrir (fora do escopo). Parsers novos (T9) não precisam de mudança: a busca lê `message`/`message_fts`.
- **Testes Relacionados**: `SearchBulkTest` (palavras parciais, ASCII sem diferenciar caixa, entrada com símbolos, filtro por app, só apagadas, contador de não lidas, fixar x retenção, exclusão e limpeza, mais de 500 ids) e `SearchViewModelsTest`. Comando: `./gradlew testDebugUnitTest`.
- **QA manual**: com mensagens de 2 apps, buscar parte de uma palavra e conferir o destaque; tocar em um resultado e ver a conversa rolar até a mensagem; ligar "Só apagadas" com a busca vazia; selecionar várias mensagens (toque longo), marcar como lida/não lida e conferir o negrito e o contador; fixar e rodar a limpeza; excluir com confirmação e ver conversas vazias sumirem; repetir em pt-BR e en.
