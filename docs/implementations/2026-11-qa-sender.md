# QA Sender: app auxiliar de QA

- **Data**: 2026-11-19
- **Autor / Agente**: Claude (subagente de implementação)
- **Branch / PR**: `chore/qa-sender`
- **Continuação de Implementação Anterior?**: Sim
- **Documentações Referenciadas**:
  - [T7: Detecção de apagadas e retenção](2026-11-deleted-retention.md) (seção 6: apagadas por remoção do repost)
  - [T1: Scaffold](2026-11-scaffold.md)
- **Arquivos Principais**: `qa-sender/` (módulo novo), `settings.gradle.kts` (`include(":qa-sender")`). O módulo `app` não foi alterado.

---

## 1. Propósito

Segundo módulo Gradle (`br.com.lugedevelopment.notificationhistory.qasender`, rótulo "NH QA Sender", minSdk 26, mesmas versões de SDK/AGP/Kotlin do `app`, Compose) que simula um app de chat postando notificações `MessagingStyle`. Permite testar o Notification History sem mensageiros reais. Sem permissão `INTERNET`; só `POST_NOTIFICATIONS` (pedida em tempo de execução na primeira abertura, Android 13+). Ferramenta de QA: não vai para release.

## 2. Como funciona

- `ChatStore`: três chats (`alice`, `bob`, `group` = "QA Group" com remetentes Carol/Dave/Erin em rodízio). Cada chat guarda até 7 mensagens (`sender`, `text`, `ts` estritamente crescente), persistidas em SharedPreferences para sobreviver à morte do processo entre comandos adb.
- `Notifier`: cada chat usa **um id de notificação estável** (1001/1002/1003) e reposta a lista completa atual a cada ação, como os mensageiros reais. Lista vazia cancela a notificação. Também posta: simples (2001), com link no texto (2002) e com BigPicture gerada por código (2003).
- "Apagar (remover)": remove a mensagem e reposta a notificação sem ela (sem mensagem nova). "Apagar (placeholder)": troca o texto por `This message was deleted`, mantendo remetente e timestamp. Os dois caminhos são os que o Ingestor detecta (ver T7).
- `CommandReceiver` (exportado, sem proteção, de propósito) executa os mesmos comandos via adb.

## 3. Comandos adb

Componente: `br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver`. Extras: `cmd` (`send|delete_remove|delete_placeholder|read|plain|link|picture|reset`), `chat` (`alice|bob|group`, padrão `alice`), `text`, `index` (posição na lista atual do chat, 0 = mais antiga), `sender` (opcional, só em `send`).

```
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd send --es chat alice --es text "Oi"
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd send --es chat group --es text "Ola grupo" --es sender Carol
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd delete_remove --es chat alice --es index 1
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd delete_placeholder --es chat alice --es index 1
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd read --es chat alice
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd plain
adb shell am broadcast -n br.com.lugedevelopment.notificationhistory.qasender/.CommandReceiver -a br.com.lugedevelopment.notificationhistory.qasender.CMD --es cmd reset
```

`link` e `picture` seguem o mesmo formato de `plain`. Texto com espaços: aspas duplas externas e simples internas conforme o shell (`--es text "Oi tudo bem"` funciona em PowerShell e bash).

## 4. Mapeamento para o roteiro de QA

| Cenário do roteiro | Comando / ação |
|---|---|
| Mensagem 1:1 capturada | `send` em `alice` ou `bob` |
| Grupo com remetente | `send` em `group` (com/sem `sender`) |
| Mensagens idênticas não são descartadas | "Send duplicate" ou dois `send` com o mesmo `text` |
| Apagada por placeholder | `delete_placeholder` com `index` da mensagem |
| Apagada por remoção do repost | `delete_remove` com `index` da mensagem |
| Ler/dispensar NÃO marca apagada | `read` (cancela a notificação) |
| Mensagem empurrada pelo limite (7) NÃO marca apagada | enviar mais de 7 mensagens no mesmo chat |
| Notificação sem MessagingStyle (genérico) | `plain` |
| Link clicável nos metadados | `link` |
| Imagem/ícone grande nos metadados | `picture` |
| Estado limpo entre rodadas | `reset` |

## 5. Gotchas

- O app precisa de permissão de notificações; o QA toca em "Permitir" (não conceder via adb). Sem ela o broadcast apenas registra um aviso no logcat (tag `QaSender`).
- Só o `NotificationListenerService` do Notification History precisa estar ativo e com captura habilitada para o pacote `br.com.lugedevelopment.notificationhistory.qasender`.
- `index` refere-se à lista **atual** (já com remoções); a UI mostra o mesmo índice entre colchetes.
- O texto do placeholder (`This message was deleted`) é dado de protocolo, igual ao que o Ingestor reconhece (`DeletedPlaceholders`); não é string de UI.
- Build: `.\gradlew.bat assembleDebug`; APK em `qa-sender/build/outputs/apk/debug/qa-sender-debug.apk`. Sem testes (ferramenta auxiliar).
