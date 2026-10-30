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

> ⚠️ **Estado atual:** `versionName = "1.04"` (que vale numericamente **1.4**), `versionCode = 2`.
> A próxima versão deve ser **maior que 1.4** — ex.: `1.4.1` (correção) ou `1.5.0` (recurso).
> **Não** use `1.0.x`: seria interpretado como anterior a `1.04`.

---

## 2. Processo de release (passo a passo)

1. Atualize o [`CHANGELOG.md`](CHANGELOG.md): mova os itens de `[Unreleased]` para a nova versão.
2. Em [`app/build.gradle.kts`](app/build.gradle.kts), suba **`versionName`** e **`versionCode`**.
3. `commit` + `push` na branch **`main`**.
4. O **GitHub Actions** ([`.github/workflows/release.yml`](.github/workflows/release.yml)) compila o
   APK **assinado** e publica/atualiza a Release `v<versionName>` com o APK como asset.
5. Pronto: quem tem uma versão anterior vê **"NOVA VERSÃO DISPONÍVEL"** no CHECK UPDATE e instala
   com um toque.

Build manual (sem CI), se precisar testar localmente:

```bash
./gradlew :app:assembleRelease
```

Gera `app/build/outputs/apk/release/app-release.apk`.

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

Os APKs são assinados com `app/mpx-release.jks` (alias `mpx`). Esse arquivo e o `keystore.properties`
**não vão para o git** (estão no `.gitignore`).

- **Local:** o build lê `keystore.properties` na raiz do projeto.
- **CI:** os valores vêm de *secrets* do repositório:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- ⚠️ **Faça backup do `.jks` + a senha** num lugar seguro. Se perder, não dá mais para assinar updates
  que instalem por cima — todos os usuários teriam que desinstalar e reinstalar.

Trocar de assinatura **debug → release** exige **uma desinstalação** (assinaturas diferentes). Depois
disso, os updates passam a instalar por cima normalmente.

---

## 5. Troubleshooting

| Sintoma | Causa provável |
|---------|----------------|
| CI falha na assinatura | faltam/erraram os 4 *secrets* do keystore |
| CHECK UPDATE: "nenhuma release publicada" | não há Release publicada (ou o CI falhou) |
| "NA ÚLTIMA VERSÃO" mesmo após publicar | versão publicada não é numericamente maior que a instalada (ver §1) |
| Update não instala por cima | APK assinado com chave diferente da instalada |
