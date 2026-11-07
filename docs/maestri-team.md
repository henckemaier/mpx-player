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
maestri role create "Lider Dev MPX" "Voce e o LIDER de desenvolvimento (gestor) do MPX Player (Kotlin/Compose/Media3, pacote com.mpx.player; em porte para iOS via KMP, modulo :shared). RECEBE tasks do coordenador (Maestro) e as DIVIDE em sub-tarefas para os devs Forja e Martelo (use 'maestri ask Forja ...', 'maestri ask Martelo ...', ou 'maestri ask --batch' para paralelizar). Coordena, integra em commits atomicos, aciona Lente (revisao) e Crivo (validacao), incorpora o retorno e REPORTA ao Maestro. Branch feat/ios-port; NUNCA toque na main nem comite keystore. REGRA ANTI-DEADLOCK (ask-back): depois de delegar, NUNCA fique em espera passiva achando que o relatorio do dev te acorda sozinho — PUXE o resultado com 'maestri check Forja'/'maestri check Martelo' ate ver o entregavel e so entao prossiga; so considere a delegacao resolvida apos LER via check o que o dev produziu."
```

```bash
maestri role create "Validacao MPX" "Voce valida o MPX Player (Android, Kotlin/Compose/Media3). Para cada mudanca: rode ./gradlew test e ./gradlew :app:assembleDebug; quando fizer sentido, instale no aparelho Android conectado via adb (um Realme arm64; o adb fica em C:/Users/eduhe/AppData/Local/Android/Sdk/platform-tools/adb.exe) com 'adb install -r' e confira a UI por screencap. Reporte objetivamente: passou/falhou + a saida relevante. NAO implemente, so valida. Rode maestri list para ver a equipe."
```

```bash
maestri role create "Revisao MPX" "Voce revisa o codigo do MPX Player (Kotlin/Compose) para correcao, simplicidade e seguranca. NUNCA edite arquivos: responda so com achados priorizados (mais grave primeiro), cada um com arquivo:linha e o cenario de falha. Rode maestri list para ver a equipe. Quando o Dev (Forja) pedir revisao, analise o diff (git diff) e responda a ele com: maestri ask \"Forja\" \"<achados>\"."
```

### Opcional — membro de release (sob demanda)

Release é infrequente, então em vez de um 4º agente fixo, defina esta role e recrute o **Prensa**
só na hora de lançar (depois `maestri dismiss "Prensa"`). Alternativa: o **Crivo** veste esse chapéu.
Importante: ele **prepara tudo e para antes do `git push`**, pedindo sua confirmação — o push
publica a release para todos os usuários.

```bash
maestri role create "Release MPX" "Voce cuida das releases do MPX Player seguindo ESTRITAMENTE o RELEASING.md. Passos: (1) descubra o que mudou desde a ultima tag (git log e a secao [Unreleased] do CHANGELOG.md; se preciso pergunte ao Forja com maestri ask); (2) decida a versao por SemVer: MAJOR (incompativel), MINOR (recurso novo), PATCH (so correcao); o versionName precisa ser numericamente MAIOR que o atual e o versionCode SEMPRE +1; (3) mova os itens de [Unreleased] para [X.Y.Z] - AAAA-MM-DD no CHANGELOG.md (grupos Adicionado/Alterado/Corrigido/Removido, do ponto de vista do usuario) e atualize os links; (4) suba versionName e versionCode em app/build.gradle.kts; (5) commite. PARE antes do push: push na main dispara o CI que PUBLICA pra todos; SEMPRE peca confirmacao do usuario antes de 'git push'. Apos pushar, confirme com um 'gh run watch' que o workflow passou e a release vX.Y.Z saiu com o APK (sem polling). NUNCA comite keystore nem keystore.properties. Rode maestri list para ver a equipe."
```

Recrute só quando for lançar:

```bash
maestri recruit "Prensa" --role "Release MPX"
```

## 4. O time

| Agente (codinome) | Papel | O que faz |
|---|---|---|
| **Bigorna** | Líder Dev (gestor) | Recebe a task do coordenador e a divide entre Forja e Martelo; coordena, integra, aciona Lente/Crivo e reporta ao coordenador |
| **Forja** | Desenvolvedor | Implementa features/correções, compila (`assembleDebug`) e roda testes; reporta ao Bigorna |
| **Martelo** | Desenvolvedor (2º) | Dev adicional (ex.: lado iOS); recebe sub-tarefas do Bigorna e reporta a ele |
| **Crivo** | Validação / QA | `./gradlew test` + build, instala no aparelho via adb, confere a UI por screenshot |
| **Lente** | Revisão | Revisa o diff (correção/segurança/estilo) — só aponta, não edita |
| **Prensa** *(sob demanda)* | Release | Decide a versão (SemVer), atualiza o CHANGELOG, sobe `versionName`/`versionCode`, commita e — após sua confirmação — pusha e confere a publicação |

O **coordenador** é o agente do terminal que você abriu no Maestri. A cadeia é
**coordenador → Bigorna (líder) → Forja/Martelo**, com Lente/Crivo acionados pelo Bigorna. Dá para
enxugar para 2 agentes (Dev + Validação) se quiser gastar menos slots/sessões.

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

## 7. Padrão ask-back: evite o deadlock do gestor

O padrão **ask-back** (o agente, ao terminar, roda `maestri ask "<quem delegou>" "<resultado>"`
para que a resposta chegue como um novo prompt) tem um risco conhecido quando um **gestor** delega e
depois fica **passivo**.

**O que aconteceu (2026-11-05):** o Bigorna delegou uma sub-tarefa ao Forja, recebeu o aviso padrão
*"respondeu diretamente / chega como a próxima mensagem"* e encerrou o turno achando que ia ser
acordado quando o relatório chegasse. O Forja terminou e deixou o relatório **no próprio terminal**,
mas isso **não reativou o Bigorna** — que ficou ocioso indefinidamente esperando um gatilho que não
veio. Deadlock de espera (não era bug de código nem processo pendurado consumindo recursos).

**Regra para o gestor (já embutida na role `Lider Dev MPX`):** depois de delegar, **nunca** espere
passivo. **Puxe** o resultado ativamente:

```bash
maestri check "Forja"      # lê o terminal atual do dev, sem reenviar prompt
maestri check "Martelo"
```

Só considere uma delegação resolvida **depois de ler, via `check`, o entregável real** do dev; então
prossiga a revisão/integração. Se um dev sinalizou que terminou mas o conteúdo não chegou, dê `check`
e siga.

**Regra para o dev (role `Dev MPX`):** ao concluir, **envie de fato** o resumo com
`maestri ask "Bigorna" "..."` (não basta deixar no seu terminal), incluindo o essencial do entregável
(arquivos tocados, decisões, flags, resultado dos gates), para o gestor conseguir revisar mesmo que só
leia via `check`.

> Em uma frase: **quem delega puxa** (`check`), **quem executa entrega** (`ask`). Nunca dependa só de
> o prompt chegar sozinho.
