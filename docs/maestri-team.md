# Time do MPX Player no Maestri

Guia para montar um time de agentes no [Maestri](https://maestri.dev) focado neste projeto
(desenvolvimento, validação e revisão).

> ⚠️ O CLI `maestri` **só funciona dentro de um terminal aberto pelo próprio Maestri**
> (ele precisa do `MAESTRI_PIPE`). Os comandos abaixo rodam **nesse terminal**, não num shell
> comum nem no app desktop do Claude Code.

---

## 1. Levar o projeto para o Maestri

No app do Maestri, abra um **terminal Claude Code na pasta do projeto**
(`E:\projetos claude\mp3-player-mobile`). Se o Maestri tiver um fluxo de "novo projeto/workspace",
aponte-o para essa pasta. Esse terminal vira o **coordenador** (o "maestro") e já tem o CLI
funcionando.

## 2. Montar o time

Duas formas — escolha uma.

### Jeito fácil: pedir ao agente do terminal

Esse agente tem o mesmo skill `maestri-manager`. Diga a ele:

> Monte um time para este projeto com três agentes conectados: **Forja** (Dev), **Crivo**
> (Validação) e **Lente** (Revisão), com os papéis do `docs/maestri-team.md`, e deixe-os
> colaborando entre si.

Ele cria as roles, recruta e conecta — cuidando das aspas.

### Jeito explícito: comandos prontos

Rode `maestri list` primeiro (para não duplicar nada), crie as roles da seção 3 e então recrute:

```bash
maestri recruit "Forja" --role "Dev MPX"
maestri recruit "Crivo" --role "Validacao MPX"
maestri recruit "Lente" --role "Revisao MPX"
```

Opcional — juntar os três num grupo no canvas:

```bash
maestri band create "MPX Squad"
```

## 3. Papéis (role prompts)

Cada prompt é uma linha só; atenção às aspas ao colar.

```bash
maestri role create "Dev MPX" "Voce e o desenvolvedor do MPX Player: app Android de player MP3 em Kotlin + Jetpack Compose + Media3 (pacote com.mpx.player), estetica retro CRT/DOS. Implemente seguindo o estilo existente: UI em ui/ (componentes em ui/components, telas em ui/screens), repositorios em data/, ViewModels expondo StateFlow. Sempre compile com ./gradlew :app:assembleDebug e rode ./gradlew test antes de concluir. Release em RELEASING.md, historico em CHANGELOG.md; NUNCA comite keystore nem keystore.properties (gitignored). Rode maestri list para ver colegas e notas. Ao terminar, peca revisao com: maestri ask \"Lente\" \"revisar: <diff>\" e validacao com: maestri ask \"Crivo\" \"validar: <o que testar>\", e incorpore o retorno."
```

```bash
maestri role create "Validacao MPX" "Voce valida o MPX Player (Android, Kotlin/Compose/Media3). Para cada mudanca: rode ./gradlew test e ./gradlew :app:assembleDebug; quando fizer sentido, instale no aparelho Android conectado via adb (um Realme arm64; o adb fica em C:/Users/eduhe/AppData/Local/Android/Sdk/platform-tools/adb.exe) com 'adb install -r' e confira a UI por screencap. Reporte objetivamente: passou/falhou + a saida relevante. NAO implemente, so valida. Rode maestri list para ver a equipe."
```

```bash
maestri role create "Revisao MPX" "Voce revisa o codigo do MPX Player (Kotlin/Compose) para correcao, simplicidade e seguranca. NUNCA edite arquivos: responda so com achados priorizados (mais grave primeiro), cada um com arquivo:linha e o cenario de falha. Rode maestri list para ver a equipe. Quando o Dev (Forja) pedir revisao, analise o diff (git diff) e responda a ele com: maestri ask \"Forja\" \"<achados>\"."
```

## 4. O time

| Agente (codinome) | Papel | O que faz |
|---|---|---|
| **Forja** | Desenvolvedor | Implementa features/correções, compila (`assembleDebug`) e roda testes |
| **Crivo** | Validação / QA | `./gradlew test` + build, instala no aparelho via adb, confere a UI por screenshot |
| **Lente** | Revisão | Revisa o diff (correção/segurança/estilo) — só aponta, não edita |

O **coordenador** é o agente do terminal que você abriu no Maestri. Dá para enxugar para 2 agentes
(Dev + Validação) se quiser gastar menos slots/sessões.

## 5. Delegar

Do coordenador, por exemplo:

```bash
maestri ask "Forja" "implemente X; ao terminar peca revisao a Lente e validacao a Crivo"
```

Para disparar vários de uma vez (em paralelo):

```bash
maestri ask --batch '{"Forja": "tarefa A...", "Crivo": "tarefa B..."}'
```

## 6. (Opcional) Isolar o trabalho do Dev

Para o Dev não mexer no mesmo checkout que você, recrute-o num **floor** git-isolado (ele trabalha
num worktree/branch próprio):

```bash
maestri floor create "Experimento" --branch feat/ideia
maestri recruit "Forja" --floor "Experimento" --role "Dev MPX"
# quando pronto e commitado, do chao:
maestri floor land "Experimento"
```
