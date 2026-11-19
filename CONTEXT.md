# CONTEXT: Notification History

## Propósito

App Android gratuito, sem anúncios e 100% offline, que guarda localmente cada notificação recebida para que o usuário consiga reler mensagens apagadas pelo remetente e consultar o histórico de notificações. Especificação completa: GitHub issue #1.

## Vocabulário ubíquo

| Termo | Significado |
|---|---|
| **Notification Snapshot** | Representação independente do Android de uma notificação postada ou removida (app, chave, horário, título, texto, remetente, flags de grupo, metadados). |
| **Ingestor** | Módulo central que recebe snapshots e aplica toda a lógica: filtro por app, parser, agrupamento, deduplicação, detecção de apagadas e persistência. Único seam de teste. |
| **Parser** | Estratégia por pacote de app que extrai conversa, remetente e texto de um snapshot. Existe um parser genérico de fallback. |
| **App** | Aplicativo de origem da notificação, com flag de captura ligada/desligada. |
| **Conversation** | Agrupamento de mensagens por app + contato/grupo. |
| **Message** | Item capturado de uma conversa, com flags `deleted`, `read` e `pinned`. |
| **Deleted message** | Mensagem cuja notificação foi substituída por um placeholder de "mensagem apagada". O conteúdo original é preservado. |
| **Attachment** | Mídia ou metadado associado a uma mensagem (link, imagem, áudio). |
| **Retention** | Política de limpeza automática: geral (padrão 90 dias) e de apagadas (configurável). Mensagens fixadas nunca expiram. |

## Papéis

- **Usuário**: dono do aparelho; concede acesso às notificações e consulta o histórico.

## Limites

- Só captura o que gerou notificação no aparelho após a instalação.
- Nenhum dado sai do aparelho.

## Fases

v0.1 captura genérica + histórico + filtro · v0.2 apagadas (WhatsApp) + busca + seleção/lidas · v0.3 outros mensageiros + metadados · v0.4 mídia + backup.
