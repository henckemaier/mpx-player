# MPX Player — Porte para iOS

> Documento vivo. Branch de trabalho: `feat/ios-port` (nunca na `main`).

## 1. Estado atual (levantamento)

App **Android puro**, ~5.800 linhas de Kotlin em 30 arquivos, pacote `com.mpx.player`:

| Camada | Arquivos | Acoplamento com Android |
|---|---|---|
| `model/` | `Models.kt` | Quase puro. Único laço: `Track.uri: android.net.Uri`. `EqPresets`, enums e lógica de formatação são Kotlin puro. |
| `ui/` (Compose) | screens/, components/, theme/ | Jetpack Compose (`androidx.compose.*`). Portável via **Compose Multiplatform**. |
| ViewModels | `ui/ViewModels.kt` | `androidx.lifecycle.ViewModel` + `StateFlow`. Lógica majoritariamente portável; dois pontos tocam Android direto (`MediaMetadataRetriever` em `readTech`, `MediaStore.createDeleteRequest` em `requestDeleteTrack`). |
| `data/` | 10 repositórios | **Forte**: `MediaStore`, `DataStore`, `DocumentFile`, `ContentResolver`, `org.json`, `HttpURLConnection`, `youtubedl-android` (NDK). |
| `playback/` | `PlayerController`, `PlaybackService`, `AudioEffects` | **Total**: Media3 ExoPlayer, `MediaSessionService`, `AudioManager`, `Equalizer`/`Virtualizer`. |

Lógica pura já isolável e testada: `PlaylistLogic`, `OverrideLogic` (cobertas por testes JUnit).

## 2. Abordagem escolhida — Kotlin Multiplatform + Compose Multiplatform

Reaproveitar o máximo de Kotlin em vez de reescrever em Swift. Estrutura de módulos:

```
:shared        (KMP)  commonMain / androidMain / iosMain
:app           (Android, com.android.application) → depende de :shared
iosApp/        (Xcode, SwiftUI host) → consome o framework Shared.xcframework
```

### O que fica COMUM (`commonMain`)
- Modelos de domínio (com `Track.uri` virando `String` neutro).
- Lógica pura: `PlaylistLogic`, `OverrideLogic`, `EqPresets`.
- ViewModels (via `androidx.lifecycle` multiplataforma, já estável) expondo `StateFlow`.
- UI inteira em **Compose Multiplatform** (telas, componentes CRT, tema).
- Camada de rede (letras: LRCLIB/NetEase) com **Ktor** no lugar de `HttpURLConnection`.
- Serialização com **kotlinx.serialization** no lugar de `org.json`.
- Persistência de settings com **DataStore multiplataforma** (1.1+) ou `multiplatform-settings`.

### O que fica ESPECÍFICO (`expect`/`actual`)
| Fronteira | Android (`actual`) | iOS (`actual`) |
|---|---|---|
| **Player** | Media3 `MediaController` + `PlaybackService` | AVFoundation (`AVQueuePlayer`, `AVAudioSession`, `MPNowPlayingInfoCenter`, `MPRemoteCommandCenter`) |
| **Biblioteca** | `MediaStore` (varredura de áudio) | `MPMediaQuery` (biblioteca do iPod) e/ou arquivos no sandbox/Files |
| **Equalizer** | `android.media.audiofx.Equalizer` | `AVAudioEngine` + `AVAudioUnitEQ` |
| **Capas/metadados** | `MediaMetadataRetriever` | `AVAsset` / `AVMetadataItem` |
| **Download (yt-dlp)** | `youtubedl-android` (NDK) | **Sem equivalente trivial** — ver riscos |
| **Platform info** | `Build` | `UIDevice` |

## 3. Primeiro passo concreto — FEITO nesta branch

Scaffold do KMP compilando, **sem quebrar o Android**:

- Catálogo (`libs.versions.toml`): plugins `kotlin-multiplatform`, `android-library`; lib `kotlinx-coroutines-core`.
- Módulo `:shared` com `androidTarget` + alvos `iosX64/iosArm64/iosSimulatorArm64` (XCFramework `Shared`). Os alvos Apple só são **registrados em host macOS** (`HostManager.hostIsMac`), para que `build`/`check`/`allTests` não quebrem no Windows.
- `commonMain`: `Platform` (`expect`), e a **fronteira do player** — `AudioPlayer` (interface, com `connect()`, `volume`/`setVolume`/`toggleMute`), `PlaybackItem`, `PlaybackState`, `VolumeState`, `RepeatMode`.
- `androidMain`/`iosMain`: `actual object Platform`; `IosAudioPlayer` (esqueleto conforme à interface, pronto para receber o AVFoundation).
- `commonTest`: `PlaybackStateTest` (roda no alvo JVM/Android).
- `:app` passa a depender de `:shared`.

**Verificação:** `./gradlew :app:assembleDebug` ✅ · `:app:testDebugUnitTest` + `:shared:testDebugUnitTest` ✅.

## 4. Ordem de migração proposta (próximos passos)

1. ✅ **FEITO.** Lógica pura (`PlaylistLogic`, `OverrideLogic`, `EqPresets`) + modelos `Playlist`/`TrackOverride` movidos para `commonMain`; testes em `commonTest`. Serialização reescrita de `org.json` → `kotlinx.serialization.json` (mesmo formato de arquivo). Verificado: `:shared:testDebugUnitTest` + `:app:assembleDebug`.
2. ✅ **FEITO.** Modelo neutralizado: `Track.uri: android.net.Uri` → `String`, e `Track`/`Album`/`Artist` movidos para `commonMain` (agora puros). Call sites Android adaptados com `Uri.parse(...)`/`.toString()` (`LibraryRepository`, `PlayerController`, `ViewModels` ×3, `CoverArtLoader.load` passa a receber `String`). Verificado: build + testes.
3. ✅ **FEITO.** Rede de letras (LRCLIB → NetEase) movida para `commonMain` como `LyricsApi` (Ktor + kotlinx.serialization, no lugar de `HttpURLConnection`/`org.json`); o parse de `.lrc` e `LyricLine` também foram para `:shared` (com teste `LyricsApiParseTest` em `commonTest`). Engine por plataforma via `expect/actual newHttpClient` (Android: `ktor-client-android`; iOS: `ktor-client-darwin`, só em host macOS). `LyricsRepository` (Android) fica só com SAF/arquivos locais e delega a busca online; `fetchSynced` virou `suspend` (ajustado `Downloader.fetchAndStoreLyrics`). Regras R8 do Ktor adicionadas. Verificado: `:shared:testDebugUnitTest`, `:app:assembleDebug` e `:app:assembleRelease` (R8). Resta `org.json` só em `UpdateRepository` (Android-only por ora).
4. ✅ **FEITO.** Lado Android do contrato `AudioPlayer` + fábrica/DI. `AndroidAudioPlayer` (em `:shared/androidMain`) adapta a lógica Media3 do `PlayerController` para `PlaybackItem`/`PlaybackState` neutros; descobre o `MediaSessionService` do próprio app via `PackageManager.queryIntentServices(MediaSessionService.SERVICE_INTERFACE)` — sem referenciar a classe do `:app` (evita dep circular; 0 ou >1 → log + desconectado). Fábrica `expect fun createAudioPlayer(PlatformContext)` em `commonMain`; `PlatformContext` é um `expect class` embrulhando o `Context` no Android (wrapper, não `typealias` — `Context` é `abstract` e o `expect class` é `final`) e vazio no iOS (ignorado). `connect()` assíncrono (`connected=true` só após o `await` do `MediaController`); `release()` idempotente; `setQueue`/`play` antes de conectar ficam pendentes e são aplicados ao conectar (paridade com o `IosAudioPlayer`). Mapeamentos puros cobertos por teste. **Não** toca UI/ViewModels nem o `PlayerController` do `:app` (segue existindo; unificação no passo 6/7). Verificado: `:app:assembleDebug` + `:shared:testDebugUnitTest`.
5. 🟡 **PARCIAL — player FEITO; biblioteca pendente.** `IosAudioPlayer` (em `:shared/iosMain`) implementa o contrato `AudioPlayer` sobre AVFoundation com paridade ao `AndroidAudioPlayer`: `AVQueuePlayer` + sessão `.playback` (ativação com degradação graciosa), buffer de fila pendente antes do `connect()`, shuffle/repetição na camada de fila (modelo `items`/`order`/`pos`), fim de faixa via `AVPlayerItemDidPlayToEndTime` (ONE reinicia, ALL faz wrap, OFF para), observador periódico para `positionMs`, e tela de bloqueio (`MPNowPlayingInfoCenter` + `MPRemoteCommandCenter`). Volume **read-only** no iOS (lê `AVAudioSession.outputVolume`; set exige `MPVolumeView`). Lógica pura da fila coberta por teste no `iosTest` (roda no `iosSimulatorArm64Test`). **Validado pelo CI macOS** (compila/linka o `iosMain`, roda os testes no simulador, monta o XCFramework). Revisão: Lente (achado ALTA do `setShuffle` corrigido). **Pendente:** biblioteca via `MPMediaQuery` (depende de um contrato de repositório comum ainda inexistente) e capa na tela de bloqueio (`MPMediaItemPropertyArtwork`). **Não** toca UI/ViewModels nem o `:app`.
6. 🟡 **EM ANDAMENTO (em fatias).** Migrar ViewModels/DI para `commonMain` e preparar a unificação `PlayerController`↔`AudioPlayer`.
   - ✅ **Fatia 1** — `EqViewModel` movido para `commonMain`.
   - ✅ **Fatia 2** — interfaces comuns de dados (library/lyrics/playlists) + `LibraryState` em `commonMain`.
   - ✅ **Fatia 3 (aditiva, baixo risco) — CONCLUÍDA.** O contrato `AudioPlayer` ganhou `shuffleQueue()`/`clearQueue()` (impl Android + iOS com paridade; guard `size<2` no iOS); `AndroidAudioPlayer` passou a pollar o volume do sistema (~1s). Exposta uma instância **nova** `audioPlayer` no `AppContainer`/`AndroidAppContainer`, **ociosa/desconectada**, coexistindo com o `PlayerController` (a troca real é a Fatia 4; `PlayerController`/`PlayerVM` **não** mudaram). Testes puros em `androidUnitTest`/`iosTest`. **Validação:** Lente (aprovado), `:app:assembleDebug` + `:shared:testDebugUnitTest` verdes, CI job **Android** verde, no-regression do Crivo no **emulador x86_64** (não no Realme arm64 — evitar apagar dados do app do usuário). O CI job **iOS** ficou **bloqueado por infra** (Risco 1 materializou — ver §6), **não** por código. Commits `1611670` (fatia) + `d5c7137` (migra o runner macOS). **Dívidas para a Fatia 4:** (a) a convenção de `PlaybackState.queueIds` após `shuffleQueue` diverge — Android reflete a **ordem de reprodução** (reordenado, `index→0`); iOS mantém a **ordem original** de `setQueue` (`index` pula). `currentId` fica correto nos dois e nada consome `queueIds` pelo contrato ainda, mas **unificar** antes de ligar a UI/VM comum. (b) `clearQueue` do contrato (esvazia **e** para) diverge do `PlayerController.clearQueue` do `:app` (mantém a faixa atual) — reconciliar o call-site do botão. (c) convenção `PlaybackItem.id = Track.key`.
   - ⬜ **Fatia 4** — `:app` passa a enfileirar/observar pelo `audioPlayer` e aposenta o `PlayerController` (resolvendo as dívidas acima).
   - ⬜ Lifecycle multiplataforma dos ViewModels.
7. Migrar a UI para Compose Multiplatform; criar `iosApp/` (host SwiftUI).

## 5. Riscos

- **Build iOS exige macOS + Xcode.** Kotlin/Native não gera binários Apple no Windows (máquina atual). Os alvos iOS estão declarados e o Android segue verde aqui, mas compilar/linkar/rodar o `iosApp` precisa de um Mac (ou CI macOS). **Maior bloqueio prático.** → decisão em §6 (CI macOS).
- **Downloader (yt-dlp):** `youtubedl-android` é NDK/Android. Sem equivalente no iOS e rejeitado pela App Store. → decisão em §6 (Android-only).
- **Biblioteca de música:** modelos de acesso divergentes (MediaStore x MPMediaQuery/sandbox). Exige abstração de repositório bem desenhada.
- **Compose Multiplatform no iOS** ainda amadurecendo; componentes CRT customizados precisam de validação visual no device.
- **Equalizer/efeitos:** APIs e capacidades diferentes; paridade de som não é garantida.
- **Esforço:** reescrever `playback/` e `data/` é o grosso do trabalho; a UI Compose é a parte mais reaproveitável.

## 6. Decisões de risco

Decididas antes de avançar a migração (aval do Maestro + parecer da Lente). Resumo: **CI macOS (A)** e **downloader Android-only (A)**.

### Risco 1 — Build/validação iOS sem Mac+Xcode (máquina atual é Windows)

**Opções avaliadas**

| | Opção | O que resolve | Custo/esforço |
|---|---|---|---|
| A | **CI GitHub Actions `macos-latest`** compilando + testando o alvo iOS a cada push | Validação **automática** do lado Kotlin (`:shared` iOS: compile + `iosSimulatorArm64Test`) | Baixo. Minutos macOS **grátis enquanto o repo for público**; 10× em repo privado |
| B | **Mac na nuvem** (EC2 mac / MacinCloud / MacStadium) | Rodar em simulador/device, `xcodebuild`, assinar `.ipa`, submeter à App Store | Médio/alto (hora ou mensalidade) |
| C | **Adiar validação iOS** — só compilar common/android no Windows | Nada no iOS; `iosMain` fica sem verificação e apodrece | Zero, mas arriscado |

**Decisão: A agora, B depois.** O CI macOS dá validação automática e barata do lado Kotlin a cada push (repo é público → sem custo). **B** entra só quando chegarmos a teste em device e submissão à App Store. **C** é o estado atual até o CI existir, mas é superado por A.

**Até onde dá sem Mac local:** escrever e compilar **todo** `commonMain` + `androidMain`; com o CI (A), também compilar `iosMain` (incl. a futura impl AVFoundation) e rodar testes comuns/nativos no simulador. **Não** dá, sem Mac: rodar em iPhone/simulador interativo, `.ipa` assinado, submissão à App Store, e validação visual do Compose MP no iOS.

**Caveats para o workflow (Lente), a aplicar quando o CI for criado:**
- **Compilar no CI ≠ app iOS roda.** Linkar o framework prova só o Kotlin. O host `iosApp/` (SwiftUI) + render do Compose MP exigem `xcodebuild -sdk iphonesimulator` + boot de simulador. Planejar isso quando houver `iosApp/`, não parar no `linkDebugFramework`.
- **`macos-latest` é Apple Silicon (arm64):** rodar testes em **`iosSimulatorArm64Test`** (não `iosX64`, que pediria Rosetta).
- **Fixar a versão do Xcode** (ex.: `maxim-lobanov/setup-xcode`): Kotlin/Native 2.0.21 tem faixa suportada; o Xcode default do runner muda e quebra com erro críptico.
- **Cache do Konan + Gradle** no CI (a dist do Kotlin/Native é grande; sem cache cada run baixa centenas de MB).
- **Custo:** reavaliar se o repo deixar de ser público (macOS passa a cobrar 10×).
- **Dependência Kotlin ↔ Xcode (descoberta no 1º run do CI).** O `macos-latest` hoje só traz **Xcode 26.x**, e o **Kotlin/Native 2.0.21** (out/2024) só foi testado até ~Xcode 16.0 — a toolchain do 26 provavelmente quebra o link/cinterop nativo. **Rota A (atual):** rodar o job iOS numa imagem mais antiga (`macos-14`) com Xcode ≤ 16.0, sem subir o Kotlin. **Rota B (definitiva):** subir o Kotlin para a linha que acompanha o Xcode 26 (~2.2.20+) — mexe em KGP + plugin do Compose compiler + reteste do Android. **Conclusão:** o porte iOS vai exigir manter o Kotlin alinhado ao Xcode do runner; a Rota A é um paliativo enquanto a imagem antiga existir.
- Casa com a **guarda de host** já aplicada (`:shared` registra alvos Apple só em macOS) → o loop no Windows segue verde enquanto o CI cuida do lado Apple.

**CI implementado** (`.github/workflows/ci.yml`): dois jobs a cada push/PR.
- **`android`** (`ubuntu-latest`): `./gradlew :app:assembleDebug test` — build debug + testes unitários (inclui o `commonTest` do `:shared` rodando no alvo Android/JVM).
- **`ios`** (`macos-15`; era `macos-14`/`macos-latest` — ver *Atualização 2026-11-05* abaixo): fixa o Xcode (`setup-xcode`), instala o **Android SDK/platform-35** (o `:shared` aplica `com.android.library`, então o Gradle configura o plugin Android mesmo para tasks iOS), cacheia `~/.konan` + Gradle, e roda `:shared:iosSimulatorArm64Test` (compila o `commonTest` para o simulador e **executa** os testes num simulador bootado) + `:shared:assembleSharedDebugXCFramework` (linka os 3 alvos iOS e monta o XCFramework).

**Até onde o CI valida o iOS sem Mac local:** compila o código Kotlin para iOS (commonMain + iosMain, incluindo a futura impl AVFoundation), roda os testes comuns no **simulador**, e prova que o **XCFramework** monta. O que ele **ainda não** cobre (até existir `iosApp/`): `xcodebuild` do host SwiftUI e render do Compose Multiplatform no simulador — há um passo `TODO` comentado no workflow para quando o `iosApp/` existir. A guarda de host mantém o dev no Windows verde; o job `android` não é afetado (o release.yml segue separado, só no push à main).

**Atualização 2026-11-05 — Risco 1 MATERIALIZOU (toolchain Xcode do CI).** Durante a Fatia 3 do Passo 6, o job iOS parou de validar:
- O GitHub **aposentou a imagem `macos-14`** (o job cancelava no despacho, 0 steps: *"The configuration 'macos-14-arm64-us-default' is not supported"*). Migramos o runner para **`macos-15`** (commit `d5c7137`), o que eliminou esse erro estrutural.
- Mas o **Xcode 15.4 foi removido de TODAS as imagens hospedadas** (do `macos-15` em 2025-05-29; política do GitHub = **1 major de Xcode por major de macOS** → `macos-15` só traz 16.x; imagens macOS-26 trazem 26.x). O `setup-xcode` passou a falhar: *"Could not find Xcode version that satisfied version spec: '15.4'"*. Houve também constrangimento **transitório** de capacidade de runner macOS-arm64 (*"job not acquired ... capacity constraints"*). **Conclusão:** o paliativo da **Rota A original** (fixar Xcode ≤16.0 numa imagem antiga) **deixou de existir** — nenhuma imagem hospedada tem mais Xcode 15.4.
- **Decisão (Maestro):** **Spike A** primeiro — fixar um **Xcode 16.x** disponível no `macos-15` **mantendo o Kotlin/Native 2.0.21**, para checar se o link/cinterop nativo sobrevive. Job iOS verde → Risco 1 resolvido via A. Falha no link/cinterop → **Rota B** (subir o Kotlin/Native para a linha que acompanha o Xcode atual) como tarefa dedicada, **só com OK do Maestro** (altera o pin de toolchain). O guard de host `HostManager.hostIsMac` permanece.

**Desfecho — Risco 1 RESOLVIDO via Rota A (sem bump de Kotlin).** O Spike A (`setup-xcode` com `xcode-version: latest-stable`) ficou **VERDE**: o job iOS compilou `iosSimulatorArm64`, rodou os testes no simulador e montou o XCFramework. Dois achados importantes:
- No `macos-15`, `latest-stable` resolve hoje para **Xcode 26.3** (não 16.x — a imagem já não oferece 16.x como latest-stable). **A premissa acima (de que Xcode 26 quebraria o link/cinterop do Kotlin/Native 2.0.21) estava ERRADA na prática:** a toolchain 2.0.21 **inalterada** linka/cinteropa e testa normalmente; o KGP apenas emite um WARNING benigno (*"selected Xcode version (26.3) is higher than the maximum known to the Kotlin Gradle Plugin"*). A **Rota B (bump do Kotlin) NÃO foi necessária.**
- **Pin policy (decidida pelo Maestro): manter `latest-stable`** no step `setup-xcode` — alvo móvel, porém **auto-curável contra a rotação/aposentadoria de imagem** (foi a aposentadoria do pino fixo 15.4 que causou todo o incidente). Pin `major.minor` explícito foi **rejeitado** (frágil a aposentadoria de versão). A **Rota B fica como gatilho futuro**: só entra se um Xcode novo quebrar de fato o K/N 2.0.21 — nesse caso o job falha explícito e aí se decide o bump.

Resultado: **Fatia 3 fechada com CI Android + iOS verde.**

### Risco 2 — Downloader (yt-dlp / `youtubedl-android`) sem equivalente iOS

**Contexto:** `youtubedl-android` empacota Python + yt-dlp + FFmpeg como libs nativas NDK — **Android-only**. Além de não haver equivalente viável no iOS, a **App Store rejeita** apps baixadores de YouTube (**Guideline 2.5.2** — código executável/intérprete embarcado — e ToS do YouTube).

**Opções avaliadas**

| | Opção | Veredito |
|---|---|---|
| A | **Android-only via `expect`/`actual`**; no iOS a feature é **ausente em tempo de compilação** | **Escolhida** |
| B | **Backend/proxy** rodando yt-dlp | **Descartada:** você passa a *operar* a extração (exposição legal/DMCA **maior**), paga hosting, e a app **ainda** é rejeitada pela 2.5.2 se a capacidade aparecer |
| C | Alternativa nativa iOS | Inexistente/viola ToS |
| D | Remover no iOS | Na prática = A, mas A é a forma arquitetural limpa |

**Decisão: A — downloader Android-only, atrás de uma fronteira `expect`/`actual` (ou interface "unsupported").** Pontos de implementação (Lente):
- A 2.5.2 inspeciona a **capacidade no binário**: no iOS a lib NDK **não entra no build** (ótimo — não há o que "remover"). Garantir **ausência em tempo de compilação** (`actual` no-op/ausente + gate na navegação), **não** um toggle oculto com a capacidade ainda embarcada.
- **`data/` e ViewModels comuns não podem depender direto do downloader**: manter atrás do `expect`/`actual` para o `commonMain` compilar no iOS sem a dependência NDK.
- **Paridade:** iOS sai **sem download** — documentado; decisão de produto/PO.
- Não altera a exposição já existente no Android (ToS do YouTube) — fora do escopo deste porte.
