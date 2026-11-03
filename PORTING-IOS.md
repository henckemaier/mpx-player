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
- Módulo `:shared` com `androidTarget` + alvos `iosX64/iosArm64/iosSimulatorArm64` (XCFramework `Shared`).
- `commonMain`: `Platform` (`expect`), e a **fronteira do player** — `AudioPlayer` (interface), `PlaybackItem`, `PlaybackState`, `RepeatMode`.
- `androidMain`/`iosMain`: `actual object Platform`; `IosAudioPlayer` (esqueleto conforme à interface, pronto para receber o AVFoundation).
- `commonTest`: `PlaybackStateTest` (roda no alvo JVM/Android).
- `:app` passa a depender de `:shared`.

**Verificação:** `./gradlew :app:assembleDebug` ✅ · `:app:testDebugUnitTest` + `:shared:testDebugUnitTest` ✅.

## 4. Ordem de migração proposta (próximos passos)

1. Mover lógica pura (`PlaylistLogic`, `OverrideLogic`, `EqPresets`) para `commonMain` + mover os testes para `commonTest`.
2. Neutralizar o modelo: `Track.uri` → `String`; adaptar os dois usos no Android.
3. Trocar `org.json` → kotlinx.serialization e `HttpURLConnection` → Ktor (letras).
4. Implementar o `actual` Android do `AudioPlayer` adaptando o `PlayerController`.
5. Implementar o `AudioPlayer` iOS sobre AVFoundation + biblioteca via `MPMediaQuery`.
6. Migrar ViewModels para `commonMain` (lifecycle multiplataforma).
7. Migrar a UI para Compose Multiplatform; criar `iosApp/` (host SwiftUI).

## 5. Riscos

- **Build iOS exige macOS + Xcode.** Kotlin/Native não gera binários Apple no Windows (máquina atual). Os alvos iOS estão declarados e o Android segue verde aqui, mas compilar/linkar/rodar o `iosApp` precisa de um Mac (ou CI macOS). **Maior bloqueio prático.**
- **Downloader (yt-dlp):** `youtubedl-android` é NDK/Android. Sem equivalente no iOS (e políticas da App Store restringem esse tipo de recurso). Provável: recurso só-Android no início, ou repensar via backend.
- **Biblioteca de música:** modelos de acesso divergentes (MediaStore x MPMediaQuery/sandbox). Exige abstração de repositório bem desenhada.
- **Compose Multiplatform no iOS** ainda amadurecendo; componentes CRT customizados precisam de validação visual no device.
- **Equalizer/efeitos:** APIs e capacidades diferentes; paridade de som não é garantida.
- **Esforço:** reescrever `playback/` e `data/` é o grosso do trabalho; a UI Compose é a parte mais reaproveitável.
