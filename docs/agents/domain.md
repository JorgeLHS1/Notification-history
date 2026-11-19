# Domain Documentation Rules

Este documento estabelece as regras de consulta e manutenção da documentação de domínio no repositório Notification History.

## 1. Single Source of Truth de Domínio: `CONTEXT.md`

- **Localização**: Raiz do repositório (`./CONTEXT.md`).
- **Papel**: Apresenta a visão de alto nível do sistema: propósito de negócio, entidades centrais, limites de contexto (bounded contexts), papéis de usuário e vocabulário ubíquo.
- **Regra para Agentes**:
  - Antes de criar ou renomear entidades, classes de modelo ou tabelas de banco de dados, consulte o `CONTEXT.md` para respeitar a nomenclatura estabelecida.
  - Se a tarefa introduzir um conceito de negócio novo, atualize o `CONTEXT.md` de forma concisa.

---

## 2. Decisões Arquiteturais: ADRs (`docs/adr/`)

- **Localização**: `docs/adr/XXXX-titulo-da-decisao.md` (sequencial numérico).
- **Template padrão**: Consulte `docs/adr/0000-template.md`.
- **Regra para Agentes**:
  - Qualquer decisão com impacto estrutural (mudança de biblioteca central, padrão de comunicação assíncrona, estratégia de persistência ou autenticação) deve ser documentada como um ADR.
  - Nunca tome decisões arquiteturais sem registrar o contexto, as opções consideradas e as consequências aceitas.

---

## 3. Registro Obrigatório de Implementações (`docs/implementations/`)

Para garantir a continuidade e a colaboração entre múltiplos agentes e sub-agentes, **toda implementação concluída deve ser documentada** antes do encerramento da tarefa:

- **Localização**: `docs/implementations/<ano-mes>-<nome-da-feature-ou-modulo>.md` (ou atualização do documento existente do módulo).
- **Template padrão**: Consulte `docs/implementations/0000-template.md`.
- **Conteúdo Mandatório**:
  1. **O que foi implementado**: Resumo executivo da funcionalidade, correção ou refatoração.
  2. **Decisões tomadas**: Por que foi feito dessa forma, trade-offs considerados e alternativas descartadas.
  3. **Como foi implementado**: Fluxo de dados, contratos de interface (APIs/IPCs/modelos), arquivos principais modificados e diagramas quando aplicável.
  4. **Guia para futuros agentes**: Pontos de extensão, gotchas, premissas assumidas e onde novos agentes devem plugar novas capacidades sem quebrar o módulo existente.
  5. **Continuação & Rastreabilidade**: Indicar se a entrega é continuação de outra(s) implementação(ões), inserindo links diretos para as documentações referenciadas e explicando o delta construído sobre a base anterior.
- **Regra Mandatória para Agentes**:
  - Nunca finalize uma tarefa nem abra PR para `staging` sem registrar ou atualizar o documento correspondente em `docs/implementations/`.
  - Ao iniciar uma nova tarefa, consulte primeiramente `docs/implementations/` para compreender os padrões estabelecidos pelos agentes anteriores antes de alterar o código.
