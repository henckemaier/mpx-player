package com.mpx.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.ComposeUIViewController
import com.mpx.player.data.CoverLoader
import com.mpx.player.data.IosAlbumArtSource
import com.mpx.player.data.IosTechInfoReader
import com.mpx.player.di.AppContainer
import com.mpx.player.di.createAppContainer
import com.mpx.player.ui.EqViewModel
import com.mpx.player.ui.LibraryViewModel
import com.mpx.player.ui.MpxShell
import com.mpx.player.ui.NowPlayingActions
import com.mpx.player.ui.PlayerViewModel
import com.mpx.player.ui.SettingsViewModel
import com.mpx.player.ui.components.HeaderLabel
import com.mpx.player.ui.components.LocalCoverLoader
import com.mpx.player.ui.components.TextButton
import com.mpx.player.ui.nav.Destination
import com.mpx.player.ui.nav.NavState
import com.mpx.player.ui.screens.BootScreen
import com.mpx.player.ui.screens.EqActions
import com.mpx.player.ui.screens.EqScreen
import com.mpx.player.ui.screens.LibraryActions
import com.mpx.player.ui.screens.LibraryScreen
import com.mpx.player.ui.screens.NowPlayingScreen
import com.mpx.player.ui.screens.QueueActions
import com.mpx.player.ui.screens.QueueScreen
import com.mpx.player.ui.screens.SettingsActions
import com.mpx.player.ui.screens.SettingsScreen
import com.mpx.player.ui.theme.LocalLandscape
import com.mpx.player.ui.theme.Mpx
import com.mpx.player.ui.theme.MpxTheme
import platform.UIKit.UIViewController

/**
 * Ponto de entrada iOS do :shared. O host SwiftUI (iosApp/) consome este símbolo como
 * `MainViewControllerKt.MainViewController()` via `import Shared`.
 *
 * Fatia NAV (P7): o host troca o antigo seletor-de-raiz MÍNIMO (2 botões Library<->Settings da
 * fatia settings) pelo [MpxShell] COMUM — a MESMA máquina de navegação do `:app` (back-stack puro
 * [NavState] + chrome [com.mpx.player.ui.components.MpxNav]/[com.mpx.player.ui.components.MpxHeader]).
 * O mapa de destinos no iOS tem as 5 telas portadas (PLAY/LIBRARY/QUEUE/EQ/CONFIG); o Boot (fatia
 * BOOT) é o SPLASH pré-shell (`if (!booted) BootScreen else MpxShell`), FORA do NavState — como não
 * há destino BOOT na barra, `enabled` libera tudo (sem aba morta). O back do iOS é um BOTÃO "< BACK"
 * no cabeçalho (NÃO há BackHandler no iOS) visível quando [NavState.canGoBack] → `nav.pop()`
 * (política pop PLENO; swipe-back nativo = follow-up).
 *
 * O [SettingsViewModel] comum persiste em NSUserDefaults (toggles de tema/cor/fonte aparecem AO
 * VIVO porque o [MpxTheme] é dirigido pelo `settings` REAL do VM). O cabeçalho usa um [HeaderLabel]
 * simples (o nome do destino) — Battery/FILES são Android-only. `version` fixa "1.0" (NÃO
 * BuildConfig → determinístico).
 *
 * Slot PLAY = [NowPlayingScreen] CHEIA (cover/meta/título/progresso/transporte + VolumeRow +
 * Console CMD.LOG/LYRICS + PlaylistPicker + Spectrum) sobre o [PlayerViewModel] já ligado — o
 * painel do gate A/B ([NowPlayingPanel]) reusa os MESMOS sub-composables (NpParts). Slot LIBRARY
 * = [LibraryScreen] (browse real). Slot CONFIG = [SettingsScreen] com slots Android = null.
 *
 * ESPERADO no simulador: a Boot abre como splash e anima (step/bar) até PRESS ENTER, que gradua p/
 * o shell; daí a nav entre as 5 telas funciona; a biblioteca/fila iOS são vazias
 * (MPMediaQuery sem device) → contadores 0; a VolumeRow controla o GANHO DE PLAYBACK DO APP
 * (AVPlayer.volume, settável) — arrastar/mutar muda o ganho, audível só no device. Conteúdo real só
 * no iPhone. Registro, não falha. O teste
 * A/B offscreen ([NowPlayingPanelRenderTest]/[NavShellRenderTest]) é INDEPENDENTE do host (renderiza
 * `sampleUi`/dummy) e segue intacto.
 *
 * Mora em iosMain → só compila em host macOS; no Windows o source set é ignorado.
 */

// Container + VM criados UMA vez por processo (lazy singletons de arquivo), nunca por
// recomposição. O connect() (ativa a AVAudioSession .playback) roda 1x, aqui, no momento em
// que o container nasce — é o "init/once" pedido pelo wiring.
private val appContainer: AppContainer by lazy {
    createAppContainer().also { it.audioPlayer.connect() }
}
private val playerViewModel: PlayerViewModel by lazy {
    PlayerViewModel(appContainer, IosTechInfoReader())
}
private val libraryViewModel: LibraryViewModel by lazy { LibraryViewModel(appContainer) }
private val settingsViewModel: SettingsViewModel by lazy { SettingsViewModel(appContainer) }
private val eqViewModel: EqViewModel by lazy { EqViewModel(appContainer) }

// Loader de capa do host LIVE: fonte REAL do iOS (MPMediaItemArtwork é follow-up → no-op por ora,
// logo o painel cai na capa gerada no simulador). O sample do A/B NÃO passa por aqui (ver
// NowPlayingPanelRenderTest + sampleCoverArt). Singleton de arquivo, criado 1x.
private val coverLoader: CoverLoader by lazy { CoverLoader(IosAlbumArtSource()) }

fun MainViewController(): UIViewController = ComposeUIViewController {
    val libUi by libraryViewModel.ui.collectAsState()
    val playerUi by playerViewModel.ui.collectAsState()
    val tech by playerViewModel.tech.collectAsState()
    val settingsUi by settingsViewModel.ui.collectAsState()
    val eq by eqViewModel.eq.collectAsState()
    val eqSel by eqViewModel.selectedBand.collectAsState()
    // Fontes extras da NowPlaying-cheia (o painel não usava): letras, volume (= GANHO DE PLAYBACK
    // DO APP, AVPlayer.volume, settável — fatia audio-volume), favorito e playlists. Todas já
    // expostas pelo PlayerViewModel comum.
    val lyrics by playerViewModel.lyrics.collectAsState()
    val volume by playerViewModel.volume.collectAsState()
    val favorite by playerViewModel.favorite.collectAsState()
    val playlists by playerViewModel.playlists.collectAsState()
    // VMs singletons estáveis → as ações (method refs) são montadas uma vez, não a cada recomposição.
    val libActions = remember { iosLibActions(libraryViewModel) }
    val setActions = remember { iosSettingsActions(settingsViewModel) }
    val eqActions = remember { iosEqActions(eqViewModel) }
    // Gate de SPLASH (fatia BOOT, SUB-D=(a)): a Boot roda ANTES do shell, FORA do NavState. Começa
    // false (mostra a Boot), `onEnter` vira true e gradua p/ o MpxShell. `remember` basta (sem
    // recriação de Activity no iOS). Reboot/voltar-pra-boot não existe no iOS (Android-only).
    var booted by remember { mutableStateOf(false) }
    // Back-stack puro ([NavState]), igual ao :app (lá é rememberSaveable; no iOS não há recriação de
    // Activity, remember basta). onSelect = goTo (reordena-sem-dup).
    var nav by remember { mutableStateOf(NavState()) }
    // Setter VIVO do nav: o lambda fecha sobre o MutableState (via delegate), então lê/escreve o
    // `nav` ATUAL no momento da chamada — nada de snapshot. Reusado no onSelect do shell e no `play`
    // da fila. `iosQueueActions` é construído na composição (NÃO em remember) justamente p/ carregar
    // esse setter vivo sem stale-capture; os outros actions são method refs estáveis (ok em remember).
    val onSelect: (Destination) -> Unit = { nav = nav.goTo(it) }
    val queueActions = iosQueueActions(playerViewModel, onSelect)
    // playerActions também toca o nav (openLibrary → LIBRARY, usado pelo estado vazio do Console):
    // build NA composição (sem remember), mesmo padrão do queueActions — carrega o setter vivo do
    // nav sem stale-capture. Os demais callbacks são method refs estáveis do VM.
    val playerActions = iosPlayerActions(playerViewModel, onSelect)
    // MpxTheme dirigido pelo `settings` REAL do VM: os toggles de tema/cor/fonte da tela CONFIG
    // recolorem a UI inteira ao vivo (não um UserSettings() fixo).
    MpxTheme(settingsUi.settings) {
        CompositionLocalProvider(
            LocalCoverLoader provides coverLoader,
            LocalLandscape provides false,
        ) {
            // Wrapper do host (bg + padding + spacedBy 10dp): o MpxShell retrato emite cabeçalho +
            // barra + conteúdo em sequência e conta com o espaçamento do Column pai (mesmo molde do :app).
            Column(
                Modifier.fillMaxSize().background(Mpx.colors.bg).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!booted) {
                    // Boot como SPLASH: hasPermission=true (iOS não tem READ_MEDIA_AUDIO → botão GRANT
                    // nunca aparece), scanned=true, fileCount = contagem REAL já exposta no state
                    // (LibraryUi.totalFiles), eqPreset = preset REAL do settings, version "1.0"
                    // (determinístico, não BuildConfig). stepOverride/barOverride NULOS → o splash
                    // anima de verdade (step/bar) até PRESS ENTER; onEnter gradua p/ o MpxShell.
                    BootScreen(
                        hasPermission = true,
                        scanned = true,
                        fileCount = libUi.totalFiles,
                        eqPreset = settingsUi.settings.eq.preset,
                        version = "1.0",
                        onRequestPermission = {},
                        onEnter = { booted = true },
                    )
                } else {
                MpxShell(
                    nav = nav,
                    destinations = mapOf(
                        // Slot PLAY: NowPlaying-CHEIA comum (cover/meta/título/progresso/transporte +
                        // VolumeRow + Console CMD.LOG/LYRICS + PlaylistPicker + Spectrum) sobre o VM
                        // ligado. Volume = ganho de playback do app, settável (ver iosPlayerActions).
                        Destination.PLAY to { NowPlayingScreen(playerUi, tech, lyrics, volume, favorite, playlists, playerActions) },
                        Destination.LIBRARY to { LibraryScreen(libUi, playerUi, libActions) },
                        // Slots Android = null (BAIXAR/IMPORTAR/UPDATE/SAÍDA somem); onReboot/
                        // onCheckUpdate = null (linha de botões some). Sem botão morto no iOS.
                        Destination.CONFIG to { SettingsScreen(settingsUi, setActions, onReboot = null) },
                        // EQ: tela comum sobre o EqViewModel; edita/persiste EqSettings (via updateEq
                        // do IosSettingsRepository). Efeito AUDÍVEL (AVAudioUnitEQ) = fatia de áudio futura.
                        Destination.EQ to { EqScreen(eq, eqSel, eqActions) },
                        // QUEUE: tela comum sobre o mesmo playerUi do PLAY. Fila vazia no simulador
                        // (sem device) → cai no estado vazio da própria tela. `play` navega p/ PLAY.
                        Destination.QUEUE to { QueueScreen(playerUi, queueActions) },
                    ),
                    onSelect = onSelect,
                    header = {
                        // Back do iOS (política pop PLENO): botão no cabeçalho, visível só quando há
                        // para onde voltar. NÃO há BackHandler no iOS; swipe nativo = follow-up.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (nav.canGoBack) {
                                TextButton("< BACK", display = true, size = 11.sp, onClick = { nav = nav.pop() })
                            }
                            HeaderLabel(nav.current.label)
                        }
                    },
                    version = "1.0",
                    // Todas as 5 telas portadas (PLAY-cheia entrou); o Boot agora é o SPLASH pré-shell
                    // (fatia BOOT), fora do NavState. Como não há destino BOOT na barra, enabled libera tudo.
                    enabled = { true },
                )
                }
            }
        }
    }
}

/**
 * [LibraryActions] do host iOS: browse REAL delegado ao [LibraryViewModel] (filtro/busca/sort/
 * play álbum-artista-faixa-playlist/abrir-fechar/remover). Os 3 gatilhos de diálogo
 * (onTrackLong/onNewPlaylist/onEditPlaylist) são NO-OP — a edição/exclusão depende de device/Uri
 * (TODO). `onOpenPlayer` é no-op (a navegação entre abas agora é a barra MpxNav).
 */
private fun iosLibActions(vm: LibraryViewModel): LibraryActions = LibraryActions(
    onFilter = vm::setFilter,
    onQuery = vm::setQuery,
    onToggleSort = vm::toggleSort,
    onPlayAlbum = vm::playAlbum,
    onPlayArtist = vm::playArtist,
    onPlayTrack = vm::playTrack,
    onOpenPlaylist = vm::openPlaylist,
    onClosePlaylist = vm::closePlaylist,
    onPlayPlaylist = vm::playPlaylist,
    onRemoveFromPlaylist = vm::removeFromPlaylist,
    onOpenPlayer = {},
    onTrackLong = {},
    onNewPlaylist = {},
    onEditPlaylist = {},
)

/**
 * [SettingsActions] do host iOS: aparência/cores/fonte/capa + toggles de áudio delegados ao
 * [SettingsViewModel] comum (persistem em NSUserDefaults via `IosSettingsRepository`). `onScan`
 * dispara a (re)varredura neutra do repositório. As ações Android-only (SAF/import, download,
 * auto-update, lyrics-sync) NÃO existem aqui — chegam pelos slots, que são null no iOS.
 */
private fun iosSettingsActions(vm: SettingsViewModel): SettingsActions = SettingsActions(
    onSetTheme = vm::setTheme,
    onSetCustomColor = vm::setCustomColor,
    onSetOled = vm::setOled,
    onSetFont = vm::setFont,
    onSetCover = vm::setCover,
    onToggleScanlines = vm::toggleScanlines,
    onToggleGlow = vm::toggleGlow,
    onToggleCursor = vm::toggleCursor,
    onToggleMono = vm::toggleMono,
    onToggleCrossfade = vm::toggleCrossfade,
    onClearMessage = vm::clearMessage,
    onScan = vm::rescan,
)

/**
 * [EqActions] do host iOS: equalizador 10-band + preamp + efeitos delegados ao [EqViewModel] comum.
 * Editam/persistem o `eq` em NSUserDefaults (via `updateEq` do `IosSettingsRepository`). O efeito
 * AUDÍVEL (AVAudioUnitEQ no grafo de áudio) é fatia de ÁUDIO futura — aqui a tela só edita/persiste.
 */
private fun iosEqActions(vm: EqViewModel): EqActions = EqActions(
    toggleOn = vm::toggleOn,
    preset = vm::pickPreset,
    setBand = vm::setBand,
    preamp = vm::preamp,
    bass = vm::toggleBass,
    surround = vm::toggleSurround,
    loudness = vm::toggleLoudness,
    reset = vm::reset,
    save = vm::saveCustom,
)

/**
 * [QueueActions] do host iOS: pick/shuffle/clear delegados ao [PlayerViewModel]; `play` dá play e
 * NAVEGA p/ PLAY via [onGoTo] — o MESMO setter vivo do nav (não um NavState capturado). Por isso
 * este builder roda NA composição (sem remember): carrega o setter atual sem stale-capture.
 */
private fun iosQueueActions(vm: PlayerViewModel, onGoTo: (Destination) -> Unit): QueueActions = QueueActions(
    pick = vm::playIndex,
    shuffle = vm::shuffleQueue,
    clear = vm::clearQueue,
    play = { vm.play(); onGoTo(Destination.PLAY) },
)

/**
 * [NowPlayingActions] do host iOS p/ o slot PLAY ([NowPlayingScreen] cheia): transporte + seek +
 * favorito + addToPlaylist delegados ao [PlayerViewModel]. `openLibrary` (toque no estado vazio do
 * Console) NAVEGA p/ LIBRARY via [onGoTo] — o MESMO setter vivo do nav (não um NavState capturado);
 * por isso o builder roda NA composição (sem remember), igual ao queueActions.
 *
 * Volume (fatia audio-volume, [D1=1a]): `setVolume`/`mute` são REAIS — ajustam o GANHO DE PLAYBACK
 * DO APP (`AVPlayer.volume`), audível no device. Arrastar a VolumeRow muda o ganho; o mute zera/
 * restaura. NÃO é o volume de sistema (iOS não expõe set por código) — diverge do Android. O efeito
 * audível só se confirma no device; o CI prova lógica + compile/link.
 */
private fun iosPlayerActions(vm: PlayerViewModel, onGoTo: (Destination) -> Unit): NowPlayingActions = NowPlayingActions(
    toggle = vm::toggle,
    next = vm::next,
    previous = vm::previous,
    seek = vm::seek,
    shuffle = vm::toggleShuffle,
    repeat = vm::cycleRepeat,
    setVolume = vm::setVolume,   // ganho de playback do app (AVPlayer.volume), settável/audível no device
    mute = vm::toggleMute,       // zera/restaura o último ganho não-zero
    openLibrary = { onGoTo(Destination.LIBRARY) },
    toggleFavorite = vm::toggleFavorite,
    addToPlaylist = vm::addCurrentToPlaylist,
)
