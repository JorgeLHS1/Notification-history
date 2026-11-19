# AGENTS.md: Notification History

App Android (Kotlin + Jetpack Compose + Room) que guarda localmente o histórico de notificações e recupera mensagens apagadas de mensageiros. Visão de domínio em `CONTEXT.md`.

## Agent skills

### Branching & Git Flow
Pipeline estrito: `feat/*` / `fix/*` -> `staging` -> `develop` -> `release/*` -> `main`. Branches protegidas, proibido push direto. Veja `docs/agents/branching.md`.

### Versioning & SemVer
Semantic Versioning 2.0.0 obrigatório. Incremento de MINOR para features (PATCH=0) e PATCH para bugs. Sincronização multi-arquivo e CHANGELOG bilíngue. Veja `docs/agents/versioning.md`.

### Testing & QA Protocols
Obrigatório testes para novas features/bugs. Ao finalizar tarefas, elabore o roteiro de QA e invoque `/qa-reviewer` antes de promover para staging. Veja `docs/agents/testing-qa.md`.

### Domain & Implementation Docs
Visão de domínio em `CONTEXT.md`, decisões arquiteturais em `docs/adr/` e histórico técnico em `docs/implementations/`. Consulte antes de codificar e documente decisões ao concluir. Veja `docs/agents/domain.md`.

### Issue Tracker & Triage
Veja `docs/agents/issue-tracker.md` e `docs/agents/triage-labels.md`.

## Regras Mandatórias para Agentes de IA

1. **Isolamento de Branches**: Nunca desenvolva diretamente nem tente executar git push nas branches `main`, `release/*`, `develop` ou `staging`. Todo trabalho deve ser feito em branches temáticas (`feat/*`, `fix/*`, `refactor/*`, `chore/*`) criadas a partir de `staging` (ou `hotfix/*` a partir de `main`).
2. **Sincronização Estrita de Versão**: Ao concluir uma funcionalidade ou correção, atualize `versionName` e `versionCode` em `app/build.gradle.kts` e registre a entrada bilíngue no `CHANGELOG.md`.
3. **Validação de Testes**: Nunca dê uma tarefa por concluída sem executar a suíte de testes (`./gradlew testDebugUnitTest`). Novas features exigem novos testes.
4. **Documentação Mandatória em `docs/`**: Todo agente ou subagente deve documentar em `docs/implementations/` (ou em ADR em `docs/adr/`) o que foi implementado, as decisões tomadas e como o código funciona tecnicamente, deixando pontos de extensão e gotchas para que futuros agentes possam consultá-los antes de novas implementações. Caso seja continuação de implementação anterior, é mandatório linkar os documentos das etapas precedentes.
5. **Handoff de QA**: Antes de submeter alterações ou abrir PR para `staging`, gere o roteiro de testes manuais e cenários de borda para o QA.

### Regras específicas do projeto

6. **Sem acesso à rede**: O app nunca declara a permissão `INTERNET` nem usa bibliotecas que exijam rede. Backup e exportação usam apenas o Storage Access Framework.
7. **Lógica só no Ingestor**: O `NotificationListenerService` apenas converte a notificação em `NotificationSnapshot` e repassa ao Ingestor. Toda regra de negócio (filtro por app, parsers, agrupamento, deduplicação, detecção de apagadas, persistência) vive no Ingestor e é testada por ele.
8. **Strings bilíngues**: Todo texto visível fica em resources, sempre com versões `pt-BR` e `en`. Nenhuma string hardcoded na UI.
9. **Código original**: O app é construído do zero. Não faça engenharia reversa, não decompile e não copie código, assets ou marcas de apps de terceiros.
10. **Sem menção a apps de referência**: Não cite nomes, pacotes ou marcas de apps concorrentes/de referência em nada que vá para commit (código, comentários, docs, mensagens de commit, CHANGELOG). Use termos genéricos como "apps existentes do mercado".
