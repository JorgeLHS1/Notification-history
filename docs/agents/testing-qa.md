# Matriz de Testes & Protocolo de QA para Agentes

Este documento define os padrões obrigatórios de testes automatizados e o protocolo de QA para entrega de tarefas no projeto Notification History.

---

## 🧪 1. Matriz de Testes Automatizados

Comandos de execução no projeto:
- **Suite Geral**: `./gradlew testDebugUnitTest`

### Regras de Cobertura para Agentes de IA:
1. **Nova Funcionalidade (`feat/*`)**: É **mandatório** criar testes automatizados (unitários ou de integração). Uma feature nunca é dada como concluída sem novos testes aprovados (verde/passando).
2. **Correção de Bug (`fix/*`)**: Siga o padrão **Red $\rightarrow$ Green**:
   - Escreva um teste de regressão que reproduza a falha (vermelho).
   - Aplique o fix para que o teste passe (verde).
3. **Refatoração (`refactor/*`)**: Todos os testes pré-existentes devem continuar passando sem alteração na asserção de comportamento esperado.

---

## 📋 2. Protocolo de Pedido de Testes de QA (Handoff)

Antes de finalizar qualquer tarefa, abrir Pull Request ou promover alterações para `staging`, o agente ou desenvolvedor deve gerar obrigatoriamente um **Plano de Testes & Roteiro de QA** em Markdown.

### Template Obrigatório do Roteiro de QA (no PR ou na entrega da tarefa):

```markdown
### 🧪 Roteiro de QA & Validação Manual

#### 1. Resumo da Mudança
- **Tipo**: [Feature / Bugfix / Refactor]
- **Objetivo**: Breve descrição do que foi implementado ou corrigido.
- **Documentação de Implementação**: Link para o arquivo correspondente em `docs/implementations/` (e ADR se houver).

#### 2. Casos de Teste Verificados pelo Agente
- [x] **TC-01 [Automatizado]**: Descrição do teste unitário/integração que validou o fluxo principal.
- [x] **TC-02 [Automatizado]**: Descrição do teste cobrindo casos de borda ou exceção.

#### 3. Roteiro Passo a Passo para o QA Humano / Revisor
1. **Pré-condições**: Estado inicial necessário, variáveis de ambiente ou dados de teste.
2. **Passo 1**: Ação a ser executada na interface, CLI ou endpoint.
3. **Passo 2**: Próxima interação.
4. **Resultado Esperado**: O que exatamente deve acontecer (e o que NÃO deve acontecer).

#### 4. Cenários Negativos & Casos de Borda Sugeridos
- [ ] Cenário com entradas inválidas ou payload vazio.
- [ ] Cenário com falha de conexão ou timeout.

#### 5. Checklist de Governança
- [x] Testes automatizados executados e passando.
- [x] Documentação técnica escrita/atualizada em `docs/implementations/` (o que foi feito, decisões e como funciona).
- [x] SemVer atualizado em todos os arquivos sincronizados e CHANGELOG bilíngue atualizado.
```

---

## 🤖 3. Papel e Invocação do `qa-reviewer`

Quando uma tarefa envolver fluxos complexos ou antes de promover para a branch `staging`:
- Invoque a skill `/qa-reviewer` (ou solicite ao sub-agente especializado) para:
  1. Comparar o diff da branch de trabalho contra `staging` (`git diff staging...HEAD`).
  2. Verificar se há casos de teste faltando na suíte automatizada.
  3. Preencher o checklist de QA e alertar sobre potenciais cenários de risco não cobertos.
