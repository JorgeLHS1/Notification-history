# Git Flow & Branching Policy para Agentes de IA

Este documento define a política obrigatória de fluxo de trabalho Git (**Git Flow**), o pipeline de promoção entre ambientes e as regras estritas de proteção de branches para agentes de IA e engenheiros no projeto Notification History.

---

## 🏗️ 1. O Pipeline Git Flow no Notification History

O fluxo oficial de integração e promoção segue rigorosamente a sequência:

$$\text{feat/* / fix/*} \longrightarrow \text{staging} \longrightarrow \text{develop} \longrightarrow \text{release/*} \longrightarrow \text{main}$$

$$\text{hotfix/*} \longrightarrow \text{main}$$

```
  main         ────────────────────────────────────────────● (vX.Y.Z) ──
                                                          /   ▲
  release                                    ────────────●    │ (hotfix/*)
                                            /                 │
  develop                       ───────────●                  │
                               /                              │
  staging          ───────────●                               │
                  /                                           │
  feat / fix  ───● (feat/* ou fix/*)                          │
                                                              │
  hotfix      ────────────────────────────────────────────────┘
```

### Sequência do Pipeline de Promoção:

1. **Desenvolvimento (`feat/*` ou `fix/*`)**:
   - Todo novo recurso (`feat/*`) ou correção (`fix/*`) é criado isoladamente em uma branch temática criada a partir de `staging` (ou `develop`).
2. **Homologação (`feat/*` / `fix/*` $\rightarrow$ `staging`)**:
   - O trabalho concluído é integrado primeiramente em `staging` para validação integrada, testes ponta a ponta e homologação.
3. **Integração Consolidada (`staging` $\rightarrow$ `develop`)**:
   - Uma vez homologadas e validadas em `staging`, as alterações são promovidas para `develop`, consolidando o estado de desenvolvimento contínuo.
4. **Preparação de Release (`develop` $\rightarrow$ `release/*`)**:
   - Quando um conjunto de funcionalidades atinge maturidade para lançamento, uma branch `release/vX.Y.Z` é criada a partir de `develop`.
   - Nela são realizados congelamento de código, ajustes de metadados de versão SemVer e verificações finais.
5. **Produção (`release/*` $\rightarrow$ `main`)**:
   - A release aprovada é mergeada em `main` e recebe a tag oficial SemVer `vX.Y.Z`.
   - As alterações de release são sincronizadas de volta para `develop` e `staging`.
6. **Correção Crítica / Emergencial (`hotfix/*` $\rightarrow$ `main`)**:
   - Em caso de falha crítica em produção (`main`), cria-se `hotfix/vX.Y.Z` diretamente a partir de `main`.
   - Após validação, o merge é feito diretamente em `main` (com tag PATCH) e sincronizado imediatamente em `staging` e `develop`.

---

## 🚫 2. Branches Protegidas & Políticas de Acesso

As seguintes branches são classificadas como **Branches Protegidas**:

| Branch | Papel | Política de Acesso & Proteção |
|---|---|---|
| `main` | Produção estável oficial com tags SemVer (`vX.Y.Z`). | **PROTEGIDA**. Proibido desenvolvimento direto. Proibido `git push` direto. |
| `release/*` | Congelamento e validação de versão para entrega. | **PROTEGIDA**. Proibido desenvolvimento direto. Proibido `git push` direto. |
| `develop` | Integração contínua e consolidação pós-staging. | **PROTEGIDA**. Proibido desenvolvimento direto. Proibido `git push` direto. |
| `staging` | Homologação, validação integrada e testes de QA. | **PROTEGIDA**. Proibido desenvolvimento direto. Proibido `git push` direto. |

> [!CAUTION]
> **Aviso para Agentes de IA**:
> Nunca realize `git checkout main`, `git checkout staging` ou `git checkout develop` para commitar alterações diretamente. Qualquer tentativa de commit ou push direto nestas branches viola os protocolos de governança deste repositório.

---

## 📋 3. Nomenclatura Padrão de Branches

- `feat/<escopo-curto-ou-issue>`: Novas funcionalidades.
- `fix/<escopo-curto-ou-issue>`: Correções de bugs.
- `refactor/<escopo-curto>`: Refatorações sem alteração de funcionalidade externa.
- `test/<escopo-curto>`: Criação ou ajuste exclusivo de testes automatizados.
- `chore/<escopo-curto>`: Manutenção de dependências, tarefas de CI/CD ou tooling.
- `hotfix/<versao>`: Correção emergencial gerada a partir de `main`.
