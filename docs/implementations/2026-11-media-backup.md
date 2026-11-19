# T10: Recuperação de mídia + backup (exportar/importar)

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `feat/12-media-backup` (issue #12, spec #1)
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T2: Ingestor core](2026-11-ingestor.md)
  - [T4: Segurança](2026-11-security.md)
  - [T5: UI](2026-11-ui.md)
  - [T7: Apagadas e retenção](2026-11-deleted-retention.md)
  - [T9: Parsers e metadados](2026-11-parsers-metadata.md)
- **Arquivos Principais Modificados**:
  - `backup/HistoryBackup.kt` (novo: formato JSON versionado em ZIP)
  - `media/MediaRecovery.kt` (novo: `MediaSource`, `SafTreeMediaSource`, `MediaMatcher`, `PrivateMediaStore`, `MediaSettings`)
  - `ingest/Ingestor.kt` (`exportBackup`, `importBackup`, `recoverMedia`), `data/HistoryDao.kt`, `data/Entities.kt` (`AttachmentType.MEDIA_FILE`; DB segue na versão 1, enum gravado como texto)
  - `ui/BackupMediaSection.kt` (novo), `ui/Screens.kt` (seção em Configurações)
  - `res/values{,-pt-rBR}/media_backup_strings.xml`
  - `app/src/test/kotlin/.../ingest/BackupMediaTest.kt`

---

## 1. O que foi implementado

- **Backup**: `Ingestor.exportBackup(out)` grava `history.json` dentro de um ZIP; `Ingestor.importBackup(in)` mescla sem duplicar. Em Configurações > Backup: botões Exportar (`ACTION_CREATE_DOCUMENT`) e Importar (`ACTION_OPEN_DOCUMENT`). Sem rede: só SAF.
- **Mídia**: em Configurações > Mídia recebida, o usuário escolhe uma pasta (`ACTION_OPEN_DOCUMENT_TREE`, permissão persistida). "Verificar agora" lista imagens/vídeos/áudios da pasta, casa por horário com mensagens que anunciaram foto (anexo `IMAGE`) e copia o arquivo para `filesDir/media/<messageId>_<nome>`, registrando um anexo `MEDIA_FILE` com o caminho.

## 2. Decisões tomadas

- **Formato v1** (`format = "notification-history-backup"`, `version = 1`): apps, conversas com mensagens aninhadas e anexos aninhados. IDs do banco não são exportados (não são portáveis). Versão maior que a suportada ou formato desconhecido → `InvalidBackupException`, nada é gravado.
- **Merge**: conversa casada por (pacote, chave); mensagem pela mesma regra de dedup do ingest (conversa + remetente + texto e mesmo `postedAt` ou mesma chave de notificação). Apps já conhecidos mantêm a configuração local. Flags (apagada, fixada, lida, `deletedAt`) das mensagens novas são restauradas; o contador de não lidas é recalculado.
- **Backup não criptografado**: o ZIP sai em texto claro (aviso na UI). Criptografar exige senha do usuário: decisão futura.
- **Arquivos de mídia não vão no backup**: só as linhas `MEDIA_FILE` (referências a caminhos privados, que não existirão em outro aparelho).
- **Matching de mídia**: arquivo com `lastModified` mais próximo do `postedAt` dentro de ±2 min, cada arquivo usado uma vez, arquivo já copiado não é oferecido de novo. Heurística: pode errar quando várias fotos chegam no mesmo minuto.
- **Mecanismo plugável**: `MediaSource` é uma interface; a cópia é um callback, então o Ingestor não conhece SAF nem disco.

## 3. Limites reais (Android 11+ / 16)

- Dados privados de outro app (`/data/data/<pkg>`, `Android/data/<pkg>`) são inacessíveis sem root. Nenhum caminho suportado contorna isso.
- `Android/data` e `Android/obb` são bloqueados no seletor SAF desde o Android 11. **`Android/media/<pkg>` não é bloqueado pela regra da plataforma**, e é onde o WhatsApp guarda a mídia recebida em versões atuais (`Android/media/com.whatsapp/WhatsApp/Media/...`). Alguns fabricantes/versões podem ainda assim restringir: **não validado em aparelho Android 16 nesta tarefa** (sem dispositivo). QA deve confirmar.
- Telegram, Instagram e Messenger em geral não gravam mídia recebida em pasta pública automaticamente; para eles, só funciona se o usuário salvar a mídia em uma pasta acessível e escolher essa pasta.
- A mídia só existe na pasta se o próprio mensageiro baixou o arquivo (auto-download). Mídia apagada pelo remetente antes do download não é recuperável.
- `lastModified` pode refletir o momento do download, não da notificação; com auto-download desligado o casamento por horário falha.
- A verificação é manual ("Verificar agora"); não há worker periódico (pode ser adicionado reaproveitando `Ingestor.recoverMedia`).
- A UI ainda não exibe a mídia copiada no balão; o dado está em `Ingestor.attachments(...)` (tipo `MEDIA_FILE`).

## 4. Guia para futuros agentes

- Nova origem de mídia: implemente `MediaSource` e chame `Ingestor.recoverMedia(source, save)`.
- Mudança de formato do backup: incremente `HistoryBackup.VERSION` e mantenha leitura de versões anteriores em `fromJson`.
- Gotcha: `MEDIA_FILE` é um valor novo de enum armazenado como texto; versões antigas do app que leiam esse banco falhariam ao converter (sem impacto hoje, DB v1 sem migração).
- Gotcha: retenção apaga a linha da mensagem (cascade no anexo), mas não o arquivo em `filesDir/media` (limpeza de órfãos pendente).

## 5. QA manual

1. Exportar: Configurações > Backup > Exportar, salvar em Downloads. Abrir o ZIP: contém `history.json`.
2. Importar o mesmo arquivo: "0 mensagens novas, N já existentes". Limpar dados do app, importar: histórico, fixadas e apagadas voltam.
3. Importar arquivo qualquer (ZIP de outra origem): mensagem "não é um backup deste app".
4. Mídia (Android 16): receber foto no WhatsApp com auto-download ligado; Escolher pasta > `Android/media/com.whatsapp/WhatsApp/Media`; Verificar agora: "copiados 1". Registrar se o seletor bloqueou a pasta.
5. Confirmar que o manifesto continua sem `INTERNET`.
