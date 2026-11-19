# Regras de Versionamento SemVer (Semantic Versioning)

Este projeto segue rigorosamente a especificação **Semantic Versioning 2.0.0** (`MAJOR.MINOR.PATCH`).

## Estrutura da Versão

A versão é composta por três números no formato `X.Y.Z`:

1. **MAJOR (`X.0.0`)**:
   - Incrementado quando ocorrem mudanças incompatíveis na API pública, quebra de contratos de comunicação ou reestruturações arquiteturais que exigem migração manual.
   - Reseta `MINOR` e `PATCH` para 0.

2. **MINOR (`1.X.0`)**:
   - **Obrigatório para implementação de novas funcionalidades**: Sempre que uma nova funcionalidade (*feature*) compatível com versões anteriores for implementada.
   - **Regra**: Incrementar a versão `MINOR` e **resetar a versão `PATCH` para 0** (ex.: `1.6.0` -> `1.7.0`).

3. **PATCH (`1.X.Y`)**:
   - Incrementado exclusivamente para correções de bugs (*bug fixes*), pequenas melhorias internas de performance, correções de tipagem ou refatorações que **não** adicionam novas funcionalidades ao usuário ou ao sistema (ex.: `1.7.0` -> `1.7.1`).

---

## Procedimento Obrigatório de Sincronização de Versão

Sempre que uma nova funcionalidade for adicionada ou correção for realizada, os agentes e desenvolvedores devem atualizar a versão **simultaneamente** nos seguintes arquivos de controle de versão:

- `app/build.gradle.kts` → `versionName = "X.Y.Z"` (SemVer) e `versionCode` (inteiro, **incrementar +1 a cada release**, nunca reutilizar).
- `CHANGELOG.md` → nova seção `## [X.Y.Z] - YYYY-MM-DD`.

> O `app/build.gradle.kts` será criado na v0.1. Até lá, a versão vive apenas no `CHANGELOG.md`.

---

## Formato do CHANGELOG.md

O `CHANGELOG.md` deve ser mantido no topo do arquivo com a nova versão seguindo as seções de idiomas configuradas:

```markdown
## [X.Y.Z] - YYYY-MM-DD

### 🇺🇸 English
- **Added**: ...
- **Fixed**: ...

### 🇧🇷 Português
- **Adicionado**: ...
- **Corrigido**: ...
```

---

## Checklist para Agentes de IA

Antes de finalizar qualquer tarefa ou abrir PR:
- [ ] O escopo inclui nova funcionalidade? Se sim, **MINOR foi incrementado** e **PATCH foi resetado para 0** (ex.: `1.6.0 -> 1.7.0`).
- [ ] O escopo é apenas correção de bug/refatoração? Se sim, **PATCH foi incrementado** (ex.: `1.7.0 -> 1.7.1`).
- [ ] Todos os arquivos de versão listados acima possuem exatamente a mesma versão sincronizada?
- [ ] O `CHANGELOG.md` contém as seções em todos os idiomas configurados?
