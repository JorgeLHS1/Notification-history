---
name: qa-reviewer
version: 1.1.0
description: Realiza auditoria de garantia de qualidade (QA) inspecionando o diff da branch atual contra staging, validando cobertura de testes automatizados e gerando o roteiro detalhado de testes para QA humano.
disable-model-invocation: true
---

# QA Reviewer & Test Plan Generator `v1.1.0`

Inspeciona as alterações realizadas na branch de trabalho atual em relação à branch `staging` (ou `develop`), audita se há testes automatizados suficientes e constrói o roteiro estruturado de testes de QA para validação humana ou de aceitação.

## Processo

### 1. Inspecionar o Diff e o Histórico
- Verifique a branch atual com `git status` e `git branch`. Se estiver diretamente em branch protegida (`main`, `develop`, `staging`), interrompa com alerta imediato.
- Inspecione as alterações comparando com a base de homologação:
  `git diff staging...HEAD` (ou `develop...HEAD` se staging ainda não existir).
- Identifique os arquivos de código alterados e os arquivos de teste correspondentes.

### 2. Auditar Cobertura de Testes & Documentação Técnica
- **Se for `feat/*`**: Verifique se novos testes unitários ou de integração foram criados e cobrem o fluxo de sucesso e casos de borda.
- **Se for `fix/*`**: Verifique se há um teste específico reproduzindo e prevenindo a regressão do bug.
- Execute a suíte de testes do projeto:
  `./gradlew testDebugUnitTest`
- Se os testes falharem ou se não houver testes cobrindo a nova lógica, alerte imediatamente antes de aprovar a transição.
- **Auditoria de Documentação em `docs/`**:
  - Verifique se a implementação, as decisões tomadas e o funcionamento técnico foram registrados em `docs/implementations/` (ou em ADR em `docs/adr/`).
  - Se a branch adicionou ou alterou funcionalidades sem documentar o "o quê", "por que" e "como", marque como **bloqueio de governança**, pois outros agentes dependerão dessa documentação para futuras implementações.

### 3. Gerar o Relatório de QA e Plano de Validação
Gere e apresente ao usuário um relatório de QA formatado contendo:
1. **Resumo das Alterações & Documentação**: Escopo técnico, impacto e link para o arquivo em `docs/implementations/`.
2. **Matriz de Casos de Teste Automatizados**: Lista dos testes executados e status.
3. **Passo a Passo para Validação Manual**: Roteiro reproduzível para o testador/QA humano (pré-condições, passos, dados de teste e resultado esperado).
4. **Cenários de Risco / Testes Negativos**: Casos de concorrência, inputs maliciosos, limites de campos ou falhas de rede recomendados para teste exploratório.

### 4. Conclusão
- Confirme se a branch está pronta para abertura de Pull Request para `staging` ou aponte os bloqueios que exigem intervenção do desenvolvedor.
