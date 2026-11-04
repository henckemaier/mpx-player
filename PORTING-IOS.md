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
4. Implementar o Android do `AudioPlayer` adaptando o `PlayerController`, e **definir a fábrica/DI** (ex.: `expect fun createAudioPlayer(ctx): AudioPlayer` ou injeção), resolvendo a passagem de `Context` no Android e o ciclo `connect()`/`release()`.
5. Implementar o `AudioPlayer` iOS sobre AVFoundation + biblioteca via `MPMediaQuery`.
6. Migrar ViewModels para `commonMain` (lifecycle multiplataforma).
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
- **`ios`** (`macos-latest`): fixa o Xcode (`setup-xcode`), instala o **Android SDK/platform-35** (o `:shared` aplica `com.android.library`, então o Gradle configura o plugin Android mesmo para tasks iOS), cacheia `~/.konan` + Gradle, e roda `:shared:iosSimulatorArm64Test` (compila o `commonTest` para o simulador e **executa** os testes num simulador bootado) + `:shared:assembleSharedDebugXCFramework` (linka os 3 alvos iOS e monta o XCFramework).

**Até onde o CI valida o iOS sem Mac local:** compila o código Kotlin para iOS (commonMain + iosMain, incluindo a futura impl AVFoundation), roda os testes comuns no **simulador**, e prova que o **XCFramework** monta. O que ele **ainda não** cobre (até existir `iosApp/`): `xcodebuild` do host SwiftUI e render do Compose Multiplatform no simulador — há um passo `TODO` comentado no workflow para quando o `iosApp/` existir. A guarda de host mantém o dev no Windows verde; o job `android` não é afetado (o release.yml segue separado, só no push à main).

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
