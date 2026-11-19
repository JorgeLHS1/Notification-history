# [ID/Nome do Módulo ou Feature]: [Título da Implementação]

- **Data**: YYYY-MM-DD
- **Autor / Agente**: [Nome do Agente / Subagente]
- **Branch / PR**: `feat/...` ou `fix/...`
- **Continuação de Implementação Anterior?**: [Sim / Não]
- **Documentações Referenciadas**:
  - [Nome da Doc Anterior](file:///docs/implementations/YYYY-MM-nome-anterior.md) *(obrigatório linkar se for continuação)*
  - [ADR Relacionado](file:///docs/adr/XXXX-titulo.md) *(se aplicável)*
- **Arquivos Principais Modificados**:
  - `path/to/main_file.ext`
  - `path/to/secondary_file.ext`

---

## 1. O Que Foi Implementado
[Descreva sucintamente o objetivo e o escopo da entrega: qual problema foi resolvido ou qual nova capacidade foi introduzida no sistema?]

### Contexto de Linhagem / Continuação *(preencher se for continuação)*:
- **Estado Anterior**: [O que a implementação anterior entregou e onde ela parou?]
- **Delta Desta Etapa**: [O que exatamente esta nova implementação acrescenta, corrige ou expande sobre a base anterior?]

---

## 2. Decisões Tomadas & Justificativas
[Explique as escolhas de design e arquitetura adotadas durante a implementação:]
- **Decisão 1**: [Por que optou por essa abordagem e quais alternativas foram descartadas?]
- **Trade-offs Aceitos**: [Quais concessões ou limitações foram assumidas conscientemente?]

---

## 3. Como Foi Implementado (Arquitetura & Detalhes Técnicos)
[Explique tecnicamente o funcionamento interno para que outro agente compreenda rapidamente sem precisar ler todo o código:]
- **Fluxo de Dados**: [Como a informação entra, é processada e sai?]
- **Contratos e Interfaces**: [Novos endpoints, payloads, assinaturas de métodos ou tabelas.]
- **Dependências e Integrações**: [Bibliotecas adicionadas ou serviços externos acionados.]

---

## 4. Guia para Futuros Agentes
[Informações essenciais para qualquer agente que for evoluir ou manter esta parte do código:]
- **Pontos de Extensão**: [Onde e como plugar novos comportamentos?]
- **Gotchas & Cuidados Especiais**: [Pegadinhas, limitações conhecidas, concorrência, estados transientes ou efeitos colaterais.]
- **Testes Relacionados**: [Quais arquivos e comandos de teste validam este módulo?]
