# Changelog

Todas as mudanças relevantes deste projeto são registradas aqui, seguindo [Semantic Versioning 2.0.0](https://semver.org/). / All notable changes to this project are documented here, following Semantic Versioning 2.0.0.

## [0.4.0] - 2026-11-19

### 🇺🇸 English
- **Added**: Local, offline notification history: every notification from any app is captured by a notification listener and stored in an encrypted database (SQLCipher, key in the Android Keystore), with no internet permission.
- **Added**: Conversations grouped by app + contact/group, with sender attribution in group chats, last message, time and app icon; filter by app and per-app capture on/off.
- **Added**: Deleted-message detection for WhatsApp and WhatsApp Business: the original text is kept and flagged as deleted, with a badge and a dedicated "deleted only" filter.
- **Added**: Parsers for Telegram, Instagram and Messenger plus a generic fallback; notification metadata (tappable links, images, large icon, actions) saved as attachments.
- **Added**: Full-text search (partial words, highlighted results that open the conversation at the message), multi-select with mark read/unread, pin and delete.
- **Added**: Retention with separate windows for general history (default 90 days) and deleted messages; pinned messages never expire.
- **Added**: Backup export/import as a versioned ZIP via the Storage Access Framework, and media recovery from a user-picked folder into app-private storage (copies are not encrypted).
- **Added**: Notification-access onboarding, Xiaomi (MIUI/HyperOS) autostart/battery guide and capture status card; biometric/device-credential lock, hidden recents preview and blocked screenshots; UI in English and Portuguese (BR).
- **Fixed**: Repeated identical messages in one chat are no longer dropped as duplicates; a "deleted" placeholder no longer flags the wrong message or leaves an empty conversation; image URIs from notifications are kept; copied media is removed with its message.
- **Fixed**: Messages deleted for everyone are now detected when the chat app simply drops them from the reposted notification (no "deleted" placeholder); the text is kept and flagged as deleted, while reading/dismissing the notification and old messages pushed out by the size cap are not.
- **Changed**: Screens refresh on database changes instead of polling, with a single query for last messages; backup and media jobs no longer delay capture; the app lock survives screen rotation; app labels are cached in the listener.

### 🇧🇷 Português
- **Adicionado**: Histórico local e offline de notificações: toda notificação de qualquer app é capturada por um listener de notificações e guardada em banco criptografado (SQLCipher, chave no Android Keystore), sem permissão de internet.
- **Adicionado**: Conversas agrupadas por app + contato/grupo, com remetente em grupos, última mensagem, horário e ícone do app; filtro por app e captura liga/desliga por app.
- **Adicionado**: Detecção de mensagens apagadas no WhatsApp e WhatsApp Business: o texto original é preservado e marcado como apagado, com selo e filtro "só apagadas".
- **Adicionado**: Parsers para Telegram, Instagram e Messenger, além de um genérico; metadados das notificações (links clicáveis, imagens, ícone grande, ações) salvos como anexos.
- **Adicionado**: Busca em texto completo (palavras parciais, resultados destacados que abrem a conversa na mensagem), seleção múltipla com marcar lida/não lida, fixar e apagar.
- **Adicionado**: Retenção com prazos separados para o histórico geral (padrão 90 dias) e para apagadas; mensagens fixadas nunca expiram.
- **Adicionado**: Exportação/importação de backup em ZIP versionado pelo Storage Access Framework e recuperação de mídia de uma pasta escolhida pelo usuário para o armazenamento privado do app (cópias sem criptografia).
- **Adicionado**: Onboarding de acesso às notificações, guia de inicialização automática/bateria da Xiaomi (MIUI/HyperOS) e cartão de status da captura; bloqueio por biometria/credencial, prévia oculta em recentes e capturas de tela bloqueadas; interface em português (BR) e inglês.
- **Corrigido**: Mensagens idênticas repetidas na mesma conversa não são mais descartadas como duplicadas; o placeholder de "apagada" não marca mais a mensagem errada nem deixa conversa vazia; URIs de imagem das notificações são preservadas; mídia copiada é removida junto com a mensagem.
- **Corrigido**: Mensagens apagadas para todos agora são detectadas quando o app de chat apenas as remove da notificação republicada (sem placeholder de "apagada"); o texto é preservado e marcado como apagado, sem marcar leitura/dispensa da notificação nem mensagens antigas empurradas pelo limite de tamanho.
- **Alterado**: Telas atualizam com mudanças no banco em vez de polling, com uma única consulta para as últimas mensagens; backup e mídia não atrasam mais a captura; o bloqueio do app sobrevive à rotação da tela; rótulos de apps ficam em cache no listener.

## [0.0.0] - 2026-11-19

### 🇺🇸 English
- **Added**: Repository governance: Git Flow, SemVer, testing/QA protocol, agent rules, domain context and documentation templates.

### 🇧🇷 Português
- **Adicionado**: Governança do repositório: Git Flow, SemVer, protocolo de testes/QA, regras para agentes, contexto de domínio e templates de documentação.
