# Processo de Release — MPX Player

Como versionar, publicar e registrar as mudanças do MPX Player.

A distribuição é por **GitHub Releases**. O app se atualiza sozinho pelo botão **CHECK UPDATE**
(CFG → `[6] SOBRE`), que baixa o APK da release mais recente e chama o instalador.

Pilha de atualização: **app (CHECK UPDATE) ← GitHub Releases ← GitHub Actions ← push na `main`**.

---

## 1. Nomenclatura de versão

Seguimos **Semantic Versioning**: `MAJOR.MINOR.PATCH` (ex.: `1.5.0`).

| Parte | Quando subir |
|-------|--------------|
| **MAJOR** | mudança incompatível ou reescrita grande |
| **MINOR** | novo recurso compatível com o que já existia |
| **PATCH** | correção de bug, sem recurso novo |

Dois campos em [`app/build.gradle.kts`](app/build.gradle.kts):

- `versionName` — a versão visível, no formato acima (ex.: `"1.5.0"`).
- `versionCode` — inteiro que **sempre aumenta** (+1 a cada release). É o que o Android usa para
  reconhecer que um APK é mais novo que o instalado.

A **tag** da release é `v<versionName>` (ex.: `v1.5.0`) — criada automaticamente pelo CI.

### Como o app decide "versão nova"

O updater compara o `versionName` **numericamente, campo a campo** (esquerda → direita; campo que
falta conta como `0`). Portanto a próxima versão precisa ser **numericamente maior** que a instalada.

> **Estado atual:** `versionName = "1.7.0"`, `versionCode = 6`.
> A próxima versão deve ser **maior que 1.7.0** — ex.: `1.7.1` (correção) ou `1.8.0` (recurso).

O módulo `:desktop-agent` (agente do PC, ver [`desktop-agent/README.md`](desktop-agent/README.md)) **não
é publicado pelo CI**: o `release.yml` só compila o APK do app. Seu empacotamento (jpackage) é manual.

---

## 2. Processo de release (passo a passo)

1. Em uma branch, atualize o [`CHANGELOG.md`](CHANGELOG.md): mova os itens de `[Unreleased]` para a
   nova versão `[X.Y.Z] - AAAA-MM-DD`.
2. Em [`app/build.gradle.kts`](app/build.gradle.kts), suba **`versionName`** e **`versionCode`**.
   **Isto é obrigatório para lançar.** Sem subir a versão, o CI não publica nada (ver §2.2).
3. Abra um **Pull Request** para a `main` e espere os checks ficarem verdes (ver §5).
4. Faça o merge. O merge na `main` dispara o workflow
   ([`.github/workflows/release.yml`](.github/workflows/release.yml)), que compila o APK **assinado**,
   guarda o `mapping.txt` e publica a Release `v<versionName>` com o APK e o mapping como assets.
5. Pronto: quem tem uma versão anterior vê **"NOVA VERSÃO DISPONÍVEL"** no CHECK UPDATE e instala
   com um toque.

### 2.1. Build manual (sem CI)

```bash
./gradlew :app:assembleRelease
```

Gera `app/build/outputs/apk/release/app-release.apk` (e o mapping em
`app/build/outputs/mapping/release/mapping.txt`).

### 2.2. O que o CI faz a cada push na `main`

- **Guarda de versão:** o CI lê o `versionName` e verifica se a tag `v<versionName>` **já existe** no
  remoto.
  - **Se existe:** build e publicação são **pulados**. O run termina em **SUCCESS**, com o aviso
    "Release v… já publicada — nada a fazer". Um push sem bump de versão é um **no-op**; a release
    existente e seu APK não são alterados.
  - **Se não existe:** compila, guarda o APK e o mapping, e publica a release.
- **Mapping do R8:** o `mapping-v<versionName>.txt` é anexado à release e também guardado como
  artefato do workflow `release-v<versionName>` (retenção de **90 dias**). Guarde-o: sem ele não dá
  para desofuscar o stack trace de um crash do APK publicado.
- **`workflow_dispatch` com `dry_run`:** compila e guarda os artefatos, mas **não** publica. Só
  publica quando roda na `main` e com `dry_run` desligado.
  - **Nota:** dry-run com a versão **já publicada** não compila nada (a guarda pula tudo). Ele só
    gera artefatos depois de um bump de versão.
- **Concorrência:** duas execuções nunca publicam ao mesmo tempo.

### 2.3. Pré-releases

- Uma pré-release (tag com sufixo, ex.: `v1.7.0-beta`) **deve** ser marcada como **pré-release** no
  GitHub.
- O updater do app consulta `/releases/latest`, que ignora pré-releases. Uma tag com sufixo que **não**
  esteja marcada como pré-release seria oferecida como atualização para todos.
- Observação: o `release.yml` não marca pré-release automaticamente. Se uma release com sufixo for
  criada por ele, ajuste a flag manualmente no GitHub.

---

## 3. Changelog

Mantemos um [`CHANGELOG.md`](CHANGELOG.md) no formato
[Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

- Toda mudança entra primeiro na seção **`[Unreleased]`**, no topo.
- Ao publicar, renomeie `[Unreleased]` para `[X.Y.Z] - AAAA-MM-DD`.
- Agrupe por tipo: **Adicionado**, **Alterado**, **Corrigido**, **Removido**.
- Escreva do ponto de vista de quem usa o app (o que mudou para o usuário), não do código.

---

## 4. Assinatura (release keystore)

Os APKs são assinados com a **keystore de release** do projeto (um `.jks` em `app/`, com alias
próprio). Esse arquivo e o `keystore.properties` **não vão para o git** (estão no `.gitignore`).

- **Local:** o build lê `keystore.properties` na raiz do projeto.
- **CI:** os valores vêm de *secrets* do repositório:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- ⚠️ **Faça backup do `.jks` + a senha** num lugar seguro. Se perder, não dá mais para assinar updates
  que instalem por cima — todos os usuários teriam que desinstalar e reinstalar.

Trocar de assinatura **debug → release** exige **uma desinstalação** (assinaturas diferentes). Depois
disso, os updates passam a instalar por cima normalmente.

---

---

## 5. Fluxo de PR e proteção da `main`

A **`main` é protegida**: não se faz commit nem push direto nela. Todo trabalho vai em uma **branch
+ Pull Request**.

- Checks **exigidos** para o merge:
  - `PR checks (build + test + lint)` — workflow [`pr-checks.yml`](.github/workflows/pr-checks.yml):
    `assembleDebug`, testes JVM e lint.
  - `R8 anti-regressão (ZipExtraField)` — workflow [`r8-guard.yml`](.github/workflows/r8-guard.yml).
- Não é exigida aprovação de revisão (o projeto tem um único responsável). Force push e deleção da
  `main` são bloqueados.
- `strict` está **desligado**: a branch não precisa estar atualizada com a `main` para o merge.
- Os administradores **não** são obrigados a cumprir a proteção (`enforce_admins` desligado). Isso
  deixa um caminho de emergência, mas significa que um admin pode contornar os checks. Use com
  cuidado.
- O merge na `main` sempre dispara o `release.yml`. Por isso, merge só depois de os checks ficarem
  verdes e com a versão certa (§2).

---

## 6. Checks, lint e testes

### Testes JVM

- Ficam em `app/src/test/` e são **versionados**.
- Rodar localmente: `./gradlew :app:testDebugUnitTest`.
- Hoje são 44 testes: `CrashLog` (11 + 11), `LyricsParse` (7), `OverrideLogic` (8) e `UpdateVersion` (7).

### Lint com baseline

- O `app/lint-baseline.xml` **registra os problemas pré-existentes**. O lint do CI falha só para
  problemas **novos**.
- Rodar localmente: `./gradlew :app:lintDebug`.
- Para **regerar** o baseline depois de corrigir ou aceitar problemas:
  `./gradlew :app:updateLintBaseline`.
- A desativação de `AndroidGradlePluginVersion` está no bloco `lint {}` de `app/build.gradle.kts`.
  Subir o AGP é uma decisão separada.
- Corrigir um item do baseline significa remover a entrada dele, e isso é trabalho de produção, em
  PR próprio.

---

## 7. R8 e a regra keep do `ZipExtraField`

**Por que a regra existe:** o `youtubedl-android` descompacta o Python embarcado na primeira
inicialização, usando o `commons-compress`. O `ExtraFieldUtils` instancia as implementações de
`ZipExtraField` (`AsiExtraField`, `X5455_ExtendedTimestamp`, `X7875_NewUnix`, `JarMarker`...) **por
reflexão**. Como nada as instancia diretamente, o R8 as torna **abstratas**. O resultado é
`ExceptionInInitializerError`, e o app fechava ao baixar. A 1.6.0 foi publicada sem a regra; a 1.6.1
já a inclui.

**A correção** está em `app/proguard-rules.pro`:

```
-keep class * implements org.apache.commons.compress.archivers.zip.ZipExtraField { public <init>(); }
```

- Qualquer **branch nova que use o downloader** (ex.: o porte iOS) precisa dessa mesma regra.
- A lib não traz regra de consumidor para isso, então ela precisa estar no projeto.

**Guarda anti-regressão:** [`tools/check-r8-zipextrafield.py`](tools/check-r8-zipextrafield.py) lê o
APK minificado e **falha** se alguma implementação de `ZipExtraField` ficar abstrata ou sem
construtor público. O workflow `r8-guard.yml` roda isso no CI.

Rodar localmente:

```bash
python tools/check-r8-zipextrafield.py app/build/outputs/apk/release/app-release.apk \
    app/build/outputs/mapping/release/mapping.txt
```

- Saída `0`: ok. Saída `1`: regressão detectada. Saída `2`: erro de uso ou ambiente (ex.: `dexdump`
  não encontrado; ele é procurado em `$DEXDUMP`, no build-tools mais recente do SDK ou no `PATH`).
- Prova local: o APK da 1.6.1 passa (exit 0); o APK da 1.6.0 publicada falha (exit 1).

---

## 8. Troubleshooting

| Sintoma | Causa provável |
|---------|----------------|
| CI falha na assinatura | faltam/erraram os 4 *secrets* do keystore |
| CI termina com "Release vX já publicada — nada a fazer" | esperado: a versão não foi subida. Suba `versionName`/`versionCode` (§2) |
| CHECK UPDATE: "nenhuma release publicada" | não há Release publicada (ou o CI falhou) |
| "NA ÚLTIMA VERSÃO" mesmo após publicar | versão publicada não é numericamente maior que a instalada (ver §1) |
| Update não instala por cima | APK assinado com chave diferente da instalada |
| Crash `ExceptionInInitializerError` / "is not a concrete class" ao baixar | regra keep do `ZipExtraField` ausente (ver §7) |
| Crash ilegível no log | desofuscar com o `mapping-v<versionName>.txt` da release (ver §2.2) |
