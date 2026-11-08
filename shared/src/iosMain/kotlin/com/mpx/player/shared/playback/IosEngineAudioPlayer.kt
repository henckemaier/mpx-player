package com.mpx.player.shared.playback

import com.mpx.player.model.EqSettings
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPlayerNode
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioUnitEQ
import platform.AVFAudio.AVAudioUnitEQFilterParameters
import platform.AVFAudio.AVAudioUnitEQFilterTypeParametric
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import platform.AVFAudio.startAndReturnError
import platform.AVFAudio.stop
import platform.Foundation.NSURL
import platform.MediaPlayer.MPMediaItemArtwork
import platform.MediaPlayer.MPMediaItemPropertyArtwork
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess
import platform.UIKit.UIImage
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue
// NOTA K/N: os membros do grafo AVAudioEngine/AVAudioPlayerNode/AVAudioFile são MEMBROS DIRETOS
// das @interface (framework AVFAudio) — chamam-se na instância SEM import (attachNode/connect/
// prepare/play/pause/scheduleFile/scheduleSegment/volume/mainMixerNode/lastRenderTime/
// playerTimeForNodeTime/length/processingFormat). Idem SUB-4: AVAudioUnitEQ/AVAudioUnitEQFilterParameters
// são CLASSES (importa o nome), mas seus membros (bands/globalGain/bypass/frequency/gain/bandwidth/
// filterType) são DIRETOS (sem import); já a constante NS_ENUM AVAudioUnitEQFilterTypeParametric é
// símbolo TOP-LEVEL → IMPORTA a constante (não qualifica pelo tipo). Diferente de AVPlayer.volume/
// setVolume (AVFoundation), de CATEGORIA → extensions que PRECISAM de import (ver kn-avfoundation-interop).

/**
 * Backend iOS **ALTERNATIVO** sobre `AVAudioEngine` + `AVAudioPlayerNode`, implementando o
 * mesmo contrato [AudioPlayer]. É selecionado por um flag de LAUNCH-TIME (`USE_ENGINE_BACKEND`
 * no `IosAppContainer`), que nasce **OFF**: em produção o player é o [IosAudioPlayer]
 * (`AVQueuePlayer`), INTOCADO. Este backend abre o caminho do grafo de áudio e, na SUB-4, landa o
 * **EQ-audível**: um `AVAudioUnitEQ` (10 bandas) é inserido entre o `playerNode` e o `mainMixer` e
 * alimentado por [eqSource] (observação de `settings.eq`, injetada só com o flag ON) via
 * [applyEqParams]. O mapeamento `EqSettings`→[EqAudioParams] é puro/testado; o timbre/largura
 * AUDÍVEL é device-gated (ver [applyEqParams]/[EQ_BANDWIDTH_OCTAVES]).
 *
 * ## Fidelidade: surround/loudness (NO-OP CONSCIENTE, sub-5)
 * Este backend — agora uma alternativa CHEIA ao `AVQueuePlayer` (sub-1 arranque + sub-2 fila/gapless
 * + sub-4 EQ + sub-3 now-playing/remote/artwork + sub-5) — aplica SOMENTE o **EQ de 10 bandas +
 * preamp** (via [applyEqParams]/[EqAudioParams]). Os campos `EqSettings.surround` (Android
 * `Virtualizer`) e `EqSettings.loudness` (Android `LoudnessEnhancer`) são **NO-OP no iOS**: não há
 * `AVAudioUnit` com equivalência 1:1, então ficam DE FORA — é um GAP CONSCIENTE de fidelidade, NÃO
 * esquecimento. O [EqAudioParams] já os EXCLUI por construção (`EqSettings.toAudioParams()` ignora
 * ambos) e o teste `EqAudioParamsTest.surroundELoudness_foraDosParams_gapDocumentado` fixa o no-op
 * do mapeamento (ligar surround+loudness não altera `bands` nem `globalGainDb`). Como o engine só
 * consome [EqAudioParams], não há ponto onde surround/loudness teriam efeito — a ausência é total e
 * intencional. Aproximá-los (ex.: `AVAudioUnitReverb`/compressão custom) seria fatia futura, se o
 * usuário pedir. Esta é a sub-5, que FECHA a frente engine-flag (5/5) — backend-engine completo
 * atrás do flag OFF, com a validação COMPORTAMENTAL (áudio/efeitos audíveis) device-gated.
 *
 * ## Estratégia de fila (SUB-2, opção A) — GAPLESS num único nó
 * O `AVAudioPlayerNode` NÃO tem fila nativa (ao contrário do `AVQueuePlayer`), mas aceita vários
 * `scheduleFile`/`scheduleSegment` ENCADEADOS no mesmo nó → reprodução gapless nativa. Mantemos
 * SEMPRE 2 itens no [pipeline]: a faixa ATUAL + a PRÓXIMA (`endAdvancePos`). No `completionHandler`
 * da atual (entregue numa thread de áudio → saltamos p/ a main com `dispatch_async`), avançamos
 * [pos] e agendamos a PRÓXIMA-próxima. Toda ação do usuário (next/prev/seek/shuffle/clear/setQueue)
 * faz `stop` + reagenda do novo [pos] e **incrementa [gen]** (token de geração): completions de
 * agendamentos antigos são DESCARTADOS por [shouldAdvance] (anti-corrida).
 *
 * A ORDEM de reprodução e o AVANÇO são lógica PURA reusada ([buildPlayOrder]/[buildShuffleAll]/
 * [queueIdsInPlayOrder]/[manualNextPos]/[endAdvancePos]) + os novos puros desta fatia
 * ([shouldAdvance]/[advanceState]/[trackPositionSeconds]/[previousAction]). Só o EFEITO
 * (scheduleFile/scheduleSegment + dispatch_async + mutar `_state`) toca AVFoundation.
 *
 * ## Posição com fila
 * O `sampleTime` do nó NÃO zera por arquivo (é contínuo no encadeamento). Guardamos
 * [trackStartSampleTime] (o `sampleTime` no início da faixa atual, atualizado a cada advance/
 * reschedule) e [seekBaseSeconds] (offset do seek dentro da faixa): posição = `framesToSeconds(
 * sampleTime - trackStartSampleTime) + seekBaseSeconds` ([trackPositionSeconds]).
 *
 * ## LIMITE de validação (device-gated)
 * O CI (job iOS no macOS) prova **COMPILE + LINK** (inclusive o binding do `completionHandler` no
 * K/N) + a MATEMÁTICA/LÓGICA pura (ordem/advance/token/posição) no `iosSimulatorArm64Test`. Ele
 * **NÃO** prova o comportamento audível: gapless sem gap, advance no instante certo, wrap, corrida
 * real, sample-rates mistos (o `sampleTime` é na taxa de render do nó, não do arquivo) — tudo
 * device-gated (não há Mac/iPhone com áudio). Com o flag OFF nada disto entra em produção.
 *
 * Threading: use na thread principal (o completion salta p/ a main). [state]/[volume] de qualquer
 * thread. Ciclo de retenção: o bloco de completion retém `this` (via nó) — [release] quebra a
 * cadeia (stop + solta refs) e o token invalida completions pendentes.
 */
@OptIn(ExperimentalForeignApi::class)
class IosEngineAudioPlayer(
    // Fonte do EQ ao vivo (SUB-4). OPCIONAL/nulável → reversível: null = sem observação (o grafo
    // monta com o eqNode em FLAT/bypass default). Único call-site = IosAppContainer (flag ON).
    private val eqSource: Flow<EqSettings>? = null,
) : AudioPlayer {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    // Paridade com a fatia de volume: ganho inicial 100 (o nó nasce 1.0), reaplicado no connect().
    private val _volume = MutableStateFlow(VolumeState(level = 100, max = 100))
    override val volume: StateFlow<VolumeState> = _volume.asStateFlow()

    private var lastNonZeroVolume: Float = 1f

    private var engine: AVAudioEngine? = null
    private var playerNode: AVAudioPlayerNode? = null
    // EQ (SUB-4): nó inserido entre playerNode e mainMixer; alimentado por eqSource (settings.eq).
    private var eqNode: AVAudioUnitEQ? = null

    // Modelo de fila (fonte da verdade): [items] na ordem original; [order] = ordem de reprodução
    // (identidade/permutação); [pos] aponta na [order]. Mesma convenção do [IosAudioPlayer].
    private var items: List<PlaybackItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var pos: Int = -1

    // Token de geração: cada stop+reschedule (ação do usuário) faz gen++; o completion captura o
    // gen do agendamento e só age se ainda for o corrente ([shouldAdvance]) → descarta obsoletos.
    private var gen: Int = 0

    // Bookkeeping de posição (ver KDoc "Posição com fila").
    private var trackStartSampleTime: Long = 0L
    private var seekBaseSeconds: Double = 0.0

    // Espelho do que está agendado no nó (cabeça = tocando agora). Sempre ≤2 itens.
    private val pipeline = mutableListOf<ScheduledItem>()

    // Fila/solicitação de play chegadas antes de o grafo existir (aplicadas no connect()).
    private var pendingQueue: Triple<List<PlaybackItem>, Int, String>? = null
    private var pendingPlay: Boolean = false

    // EQ corrente cacheado: se uma emissão chegar antes de o eqNode existir, guarda aqui e o
    // connect() reaplica ao montar (análogo ao node.volume). Scope da observação de settings.eq.
    private var lastEqParams: EqAudioParams? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Um item agendado no nó: a posição na [order] + o arquivo e seus metadados (p/ posição/duração). */
    private class ScheduledItem(
        val orderPos: Int,
        val file: AVAudioFile,
        val frames: Long,
        val sampleRate: Double,
    )

    override fun connect() {
        if (engine != null) return
        activateSession() // degrada graciosamente se falhar (não crasha)

        val e = AVAudioEngine()
        val node = AVAudioPlayerNode()
        e.attachNode(node)
        // SUB-4: insere o AVAudioUnitEQ (10 bandas) ENTRE o playerNode e o mainMixer → ponto de
        // inserção do EQ-audível (playerNode -> eq -> mainMixer). format=null: usa o formato de saída
        // do nó (casar com o processingFormat do arquivo é refinamento device-gated; aqui o foco é
        // montar/linkar o grafo).
        val eq = AVAudioUnitEQ(numberOfBands = 10.convert())
        e.attachNode(eq)
        e.connect(node, to = eq, format = null)
        e.connect(eq, to = e.mainMixerNode, format = null)
        e.prepare()
        val started = runCatching { e.startAndReturnError(null) }.getOrDefault(false)
        if (!started) println("IosEngineAudioPlayer: AVAudioEngine não iniciou; seguindo mesmo assim")

        engine = e
        playerNode = node
        eqNode = eq
        node.volume = _volume.value.level / 100f // reaplica alvo pré-conexão (no-op no default 100)
        // Aplica o EQ corrente ao montar (análogo ao node.volume reaplicado): params cacheados antes
        // do grafo, ou FLAT default se ainda não houve emissão. A observação abaixo mantém ao vivo.
        lastEqParams?.let { applyEqParams(it) }
        // Observa settings.eq (injetado só com o flag ON) → reaplica no eqNode (espelha o Android:
        // EQ fora do contrato, dirigido por flow). O guard `engine != null` no topo garante que a
        // coleta inicia UMA vez; a 1ª emissão aplica o EQ atual.
        eqSource?.let { src ->
            scope.launch {
                src.map { it.toAudioParams() }.distinctUntilChanged().collect { applyEqParams(it) }
            }
        }
        setupRemoteCommands() // bind dos comandos do lock-screen (sub-3)
        _state.update { it.copy(connected = true) }

        pendingQueue?.let { (its, si, sl) ->
            setQueue(its, si, sl) // agora o nó existe → agenda
            pendingQueue = null
        }
        if (pendingPlay) {
            pendingPlay = false
            play()
        }
    }

    /**
     * EFEITO device-gated (SÓ este método toca o `AVAudioUnitEQ`). Reaplica TODO o [EqAudioParams] no
     * [eqNode]: `bypass` (enabled=false → bypass, herdado de `AVAudioUnitEffect`), `globalGain` (o
     * preamp) e as 10 bandas paramétricas (`filterType`/`frequency`/`bandwidth`/`gain`). Update ao
     * vivo = re-chamar (re-seta tudo, SEM reconstruir o grafo). Antes de o grafo existir, só CACHEIA
     * em [lastEqParams] (o `connect()` aplica ao montar).
     *
     * O CI prova COMPILE+LINK (os membros de `AVAudioUnitEQ`/`AVAudioUnitEQFilterParameters` e a
     * constante `AVAudioUnitEQFilterTypeParametric` resolvem no K/N) + o MAPEAMENTO (já testado em
     * `EqAudioParamsTest`). O timbre/largura AUDÍVEL e o update-ao-vivo-audível são device-gated (sem
     * Mac/iPhone) — ver [EQ_BANDWIDTH_OCTAVES]. Flag OFF → nem instancia.
     */
    private fun applyEqParams(p: EqAudioParams) {
        val eq = eqNode ?: run { lastEqParams = p; return }
        lastEqParams = p
        eq.bypass = !p.enabled
        eq.globalGain = p.globalGainDb
        p.bands.forEachIndexed { i, b ->
            (eq.bands.getOrNull(i) as? AVAudioUnitEQFilterParameters)?.let { fp ->
                fp.filterType = AVAudioUnitEQFilterTypeParametric
                fp.frequency = b.freqHz
                fp.bandwidth = EQ_BANDWIDTH_OCTAVES
                fp.gain = b.gainDb
                fp.bypass = false
            }
        }
    }

    /** Sessão de áudio .playback (molde do [IosAudioPlayer]). Falha não derruba o connect. */
    private fun activateSession() {
        runCatching {
            val s = AVAudioSession.sharedInstance()
            s.setCategory(AVAudioSessionCategoryPlayback, null)
            s.setActive(true, null)
        }.onFailure { println("IosEngineAudioPlayer: AVAudioSession não ativou: $it") }
    }

    // --- Tela de bloqueio (now-playing / remote / artwork) — fiação PRÓPRIA do engine ---
    // TODO(engine-graduation): unificar a fiação now-playing/remote/artwork do engine com o
    // IosAudioPlayer QUANDO o engine graduar a padrão (device) — hoje duplicada de propósito p/
    // manter a produção (AVQueuePlayer) byte-intocada (decisão [SD]=(b) da sub-3). A dup estreita
    // (setup/teardownRemoteCommands + publishNowPlaying + loadArtworkAsync aqui; buildNowPlayingInfo
    // e loadArtwork em IosNowPlaying.kt) sai junto com o backend AVQueuePlayer quando/se o flag cair.

    /** Comandos remotos do lock-screen/Control Center (molde do [IosAudioPlayer]). Métodos já existem. */
    private fun setupRemoteCommands() {
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        cc.playCommand.addTargetWithHandler { play(); MPRemoteCommandHandlerStatusSuccess }
        cc.pauseCommand.addTargetWithHandler { pause(); MPRemoteCommandHandlerStatusSuccess }
        cc.togglePlayPauseCommand.addTargetWithHandler { toggle(); MPRemoteCommandHandlerStatusSuccess }
        cc.nextTrackCommand.addTargetWithHandler { next(); MPRemoteCommandHandlerStatusSuccess }
        cc.previousTrackCommand.addTargetWithHandler { previous(); MPRemoteCommandHandlerStatusSuccess }
    }

    private fun teardownRemoteCommands() {
        val cc = MPRemoteCommandCenter.sharedCommandCenter()
        cc.playCommand.removeTarget(null)
        cc.pauseCommand.removeTarget(null)
        cc.togglePlayPauseCommand.removeTarget(null)
        cc.nextTrackCommand.removeTarget(null)
        cc.previousTrackCommand.removeTarget(null)
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    /**
     * Publica o now-playing da faixa atual: campos textuais+tempo via [buildNowPlayingInfo] (puro),
     * depois a capa assíncrona ([loadArtworkAsync]). Escalares vêm do ENGINE (não de um AVPlayer):
     * duração=[currentDurationSec], decorrido=[currentPositionSeconds], rate=1/0 por `isPlaying`.
     * device-gated: o lock-screen EXTRAPOLA o decorrido entre updates via rate+timestamp → publicar
     * em eventos discretos basta (sem push por-tick).
     */
    private fun publishNowPlaying() {
        val item = items.getOrNull(order.getOrNull(pos) ?: -1) ?: return
        val rate = if (_state.value.isPlaying) 1f else 0f
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo =
            buildNowPlayingInfo(item, currentDurationSec(), currentPositionSeconds(), rate)
        loadArtworkAsync(item)
    }

    /**
     * Carrega a capa FORA da main e MESCLA [MPMediaItemPropertyArtwork] no dicionário mais recente do
     * NowPlaying — sem derrubar os textuais. Anti-race: captura o id no início e só aplica se a faixa
     * ainda for a atual ([shouldApplyArtwork]). Molde do [IosAudioPlayer]; [loadArtwork] é a dup de
     * IosNowPlaying.kt. Falha de load degrada graciosamente (fica sem capa).
     */
    private fun loadArtworkAsync(item: PlaybackItem) {
        val uri = item.artworkUri ?: return
        val startedId = item.id // capturado p/ o anti-race
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.convert(), 0.convert())) {
            val image = loadArtwork(uri) ?: return@dispatch_async
            dispatch_async(dispatch_get_main_queue()) {
                val currentId = items.getOrNull(order.getOrNull(pos) ?: -1)?.id
                if (!shouldApplyArtwork(startedId, currentId)) return@dispatch_async // faixa trocou: descarta
                val center = MPNowPlayingInfoCenter.defaultCenter()
                val merged = HashMap<Any?, Any?>()
                center.nowPlayingInfo?.let { merged.putAll(it) }
                merged[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize = image.size) { _ -> image }
                center.nowPlayingInfo = merged
            }
        }
    }

    override fun setQueue(items: List<PlaybackItem>, startIndex: Int, sourceLabel: String) {
        if (items.isEmpty()) return
        this.items = items
        val start = startIndex.coerceIn(0, items.lastIndex)
        // Shuffle LIGADO → permutação completa (convenção UNIFICADA da fatia queue-contrato, igual ao
        // [IosAudioPlayer]: FLAG = permutação completa, atual onde cair — NÃO start-no-topo).
        order = if (_state.value.shuffle) buildShuffleAll(items.size) else buildPlayOrder(items.size, start, shuffle = false)
        pos = order.indexOf(start).coerceAtLeast(0)
        _state.update {
            it.copy(queueIds = queueIdsInPlayOrder(items, order), index = pos, sourceLabel = sourceLabel)
        }
        if (playerNode == null) {
            pendingQueue = Triple(items, startIndex, sourceLabel) // aplica no connect()
            return
        }
        // Agenda SEM forçar play (play explícito). Preserva isPlaying: trocar a fila tocando segue
        // tocando a nova atual — paridade com o [IosAudioPlayer] (loadQueue não reinicia o play).
        rescheduleFromCurrent(0.0)
        publishNowPlaying()
    }

    /** Abre o arquivo de um item (null = falha, degrada graciosamente). */
    private fun openFile(item: PlaybackItem): AVAudioFile? {
        val url = NSURL.URLWithString(item.uri) ?: NSURL.fileURLWithPath(item.uri)
        return runCatching { AVAudioFile(forReading = url, error = null) }.getOrNull()
            ?: run { println("IosEngineAudioPlayer: falha ao abrir AVAudioFile de ${item.uri}"); null }
    }

    /**
     * Agenda a faixa em `op` (posição na [order]) no nó, com `completionHandler` que — na MAIN —
     * chama [onItemCompleted] carregando o `playGen` capturado e os frames desta faixa. `startSec`>0
     * usa `scheduleSegment` (seek); senão `scheduleFile`. Retorna o [ScheduledItem] (null = falha).
     */
    private fun scheduleOrderPos(op: Int, playGen: Int, startSec: Double = 0.0): ScheduledItem? {
        val node = playerNode ?: return null
        val item = items.getOrNull(order.getOrNull(op) ?: -1) ?: return null
        val f = openFile(item) ?: return null
        val sr = f.processingFormat.sampleRate
        val frames = f.length
        val startFrame = if (startSec > 0.0) {
            secondsToFrames(startSec.coerceIn(0.0, framesToSeconds(frames, sr)), sr).coerceIn(0L, frames)
        } else {
            0L
        }
        val completion: () -> Unit = {
            dispatch_async(dispatch_get_main_queue()) { onItemCompleted(playGen, frames) }
        }
        if (startFrame <= 0L) {
            node.scheduleFile(f, atTime = null, completionHandler = completion)
        } else {
            val remaining = frames - startFrame
            if (remaining <= 0L) return null
            node.scheduleSegment(
                f,
                startingFrame = startFrame,
                frameCount = remaining.convert(),
                atTime = null,
                completionHandler = completion,
            )
        }
        return ScheduledItem(op, f, frames, sr)
    }

    /**
     * Para o nó, limpa o [pipeline], BUMPA o token e reagenda a faixa atual (de `seekSec`) + a
     * próxima (gapless). Toca de novo se estava tocando. Usado por toda ação de navegação/seek.
     */
    private fun rescheduleFromCurrent(seekSec: Double) {
        val node = playerNode ?: return
        node.stop()
        pipeline.clear()
        gen++
        val g = gen
        trackStartSampleTime = 0L // stop reinicia o relógio do nó
        seekBaseSeconds = seekSec
        if (order.isNotEmpty() && pos in order.indices) {
            scheduleOrderPos(pos, g, seekSec)?.let { pipeline.add(it) }
            val nextOp = endAdvancePos(pos, order.size, _state.value.repeat)
            if (nextOp != null) scheduleOrderPos(nextOp, g)?.let { pipeline.add(it) }
        }
        if (_state.value.isPlaying) node.play()
        _state.update { it.copy(index = pos) }
        syncState()
    }

    /** Agenda o pipeline a partir de [pos] SE estiver vazio (sem tocar). Usado pelo [play]. */
    private fun ensureScheduled() {
        val node = playerNode ?: return
        if (pipeline.isNotEmpty() || order.isEmpty() || pos !in order.indices) return
        gen++
        val g = gen
        trackStartSampleTime = 0L
        seekBaseSeconds = 0.0
        scheduleOrderPos(pos, g)?.let { pipeline.add(it) }
        val nextOp = endAdvancePos(pos, order.size, _state.value.repeat)
        if (nextOp != null) scheduleOrderPos(nextOp, g)?.let { pipeline.add(it) }
    }

    /**
     * Fim de uma faixa (na MAIN). Descarta se obsoleto (token). Senão avança [pos] via
     * [advanceState] (ONE=mesmo pos reagenda; ALL=wrap; OFF no fim=para), remove a cabeça do
     * [pipeline] (a que terminou; a nova cabeça já está tocando gapless) e agenda a próxima-próxima.
     */
    private fun onItemCompleted(playGen: Int, endedFrames: Long) {
        if (!shouldAdvance(playGen, gen)) return // agendamento obsoleto (houve stop+reschedule)
        val repeat = _state.value.repeat
        val adv = advanceState(pos, order.size, repeat, trackStartSampleTime, endedFrames)
        if (adv == null) {
            // OFF no fim da fila: para no último.
            pipeline.clear()
            _state.update { it.copy(isPlaying = false) }
            syncState()
            return
        }
        pos = adv.pos
        trackStartSampleTime = adv.trackStartSampleTime
        seekBaseSeconds = adv.seekBaseSeconds
        if (pipeline.isNotEmpty()) pipeline.removeAt(0) // tira a que terminou; cabeça = nova atual
        val nextOp = endAdvancePos(pos, order.size, repeat)
        if (nextOp != null) scheduleOrderPos(nextOp, gen)?.let { pipeline.add(it) }
        _state.update { it.copy(index = pos) }
        syncState()
        publishNowPlaying() // advance gapless: atualiza a faixa no lock-screen
    }

    override fun play() {
        val node = playerNode
        if (node == null || order.isEmpty()) {
            pendingPlay = true // toca quando o grafo/fila entrar
            return
        }
        ensureScheduled()
        node.play()
        _state.update { it.copy(isPlaying = true) }
        syncState()
        publishNowPlaying()
    }

    override fun pause() {
        playerNode?.pause()
        _state.update { it.copy(isPlaying = false) }
        syncState()
        publishNowPlaying()
    }

    override fun toggle() {
        if (_state.value.isPlaying) pause() else play()
    }

    override fun next() {
        if (order.isEmpty()) return
        val np = manualNextPos(pos, order.size, _state.value.repeat) ?: return // fim sem repeat: no-op
        pos = np
        rescheduleFromCurrent(0.0) // nova faixa do início; preserva isPlaying
        publishNowPlaying()
    }

    override fun previous() {
        if (order.isEmpty()) return
        when (val act = previousAction(pos, order.size, currentPositionSeconds(), _state.value.repeat)) {
            is PrevAction.Restart -> rescheduleFromCurrent(0.0)
            is PrevAction.GoTo -> { pos = act.pos; rescheduleFromCurrent(0.0) }
        }
        publishNowPlaying()
    }

    override fun seekTo(positionMs: Long) {
        rescheduleFromCurrent(positionMs.coerceAtLeast(0) / 1000.0)
    }

    override fun seekToFraction(fraction: Float) {
        rescheduleFromCurrent(fractionToSeconds(fraction, currentDurationSec()))
    }

    override fun seekToIndex(index: Int) {
        if (index !in order.indices) return
        pos = index
        _state.update { it.copy(isPlaying = true) } // selecionar faixa implica tocar (ação explícita)
        rescheduleFromCurrent(0.0)
        publishNowPlaying()
    }

    override fun setShuffle(enabled: Boolean) {
        _state.update { it.copy(shuffle = enabled) }
        if (playerNode == null || items.isEmpty()) return
        val current = order.getOrNull(pos) ?: 0 // índice ORIGINAL da faixa atual
        // FLAG = permutação completa (atual preservada ONDE CAIR); desligar volta à ordem de inserção.
        order = if (enabled) buildShuffleAll(items.size) else buildPlayOrder(items.size, current, shuffle = false)
        pos = order.indexOf(current).coerceAtLeast(0)
        rescheduleFromCurrent(currentPositionSeconds()) // preserva a posição da faixa atual
    }

    override fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeat = mode) }
        if (playerNode == null || pipeline.isEmpty()) return
        // Reagenda p/ recomputar a PRÓXIMA conforme o novo modo (ONE/ALL/OFF). Preserva a posição
        // da atual (há um pequeno re-schedule da faixa — device-gated; flag OFF).
        rescheduleFromCurrent(currentPositionSeconds())
    }

    override fun shuffleQueue() {
        if (items.size < 2) return // paridade: fila de 0/1 não reembaralha
        val current = order.getOrNull(pos) ?: 0
        order = buildPlayOrder(items.size, current, shuffle = true) // atual no TOPO (ação "shuffle agora")
        pos = order.indexOf(current).coerceAtLeast(0)
        rescheduleFromCurrent(currentPositionSeconds()) // NÃO altera _state.shuffle
        publishNowPlaying()
    }

    override fun clearUpcoming() {
        if (items.size <= 1) return
        val current = items.getOrNull(order.getOrNull(pos) ?: -1) ?: return
        items = listOf(current)
        order = listOf(0)
        pos = 0
        _state.update { it.copy(queueIds = listOf(current.id), index = 0) }
        // Colapsa p/ só a atual: reagenda sem a "próxima" (preserva a posição). O AVAudioPlayerNode
        // não remove um segmento específico, então o drop dos próximos é via stop+reschedule.
        rescheduleFromCurrent(currentPositionSeconds())
        publishNowPlaying()
    }

    override fun clearQueue() {
        playerNode?.stop()
        pipeline.clear()
        gen++
        items = emptyList()
        order = emptyList()
        pos = -1
        trackStartSampleTime = 0L
        seekBaseSeconds = 0.0
        pendingQueue = null
        pendingPlay = false
        _state.update { it.copy(queueIds = emptyList(), index = -1, isPlaying = false, positionMs = 0L) }
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    override fun setVolume(fraction: Float) {
        val fr = fraction.coerceIn(0f, 1f)
        // AVAudioPlayerNode.volume é @property direta → atribuição (K/N expõe como var; não há
        // setVolume de categoria como no AVPlayer). Safe-call-assign não existe → via let.
        playerNode?.let { it.volume = fr }
        _volume.update { it.copy(level = volumeLevel(fr), max = 100, muted = fr == 0f) }
    }

    override fun toggleMute() {
        val cur = playerNode?.volume ?: (_volume.value.level / 100f)
        val (nv, nlz) = applyMuteToggle(cur, lastNonZeroVolume)
        lastNonZeroVolume = nlz
        playerNode?.let { it.volume = nv }
        _volume.update { it.copy(level = volumeLevel(nv), max = 100, muted = nv == 0f) }
    }

    override fun release() {
        scope.cancel() // encerra a observação de settings.eq (quebra o retain via closure)
        playerNode?.stop()
        engine?.stop()
        playerNode = null
        engine = null
        eqNode = null
        pipeline.clear()
        items = emptyList()
        order = emptyList()
        pos = -1
        trackStartSampleTime = 0L
        seekBaseSeconds = 0.0
        pendingQueue = null
        pendingPlay = false
        teardownRemoteCommands() // solta os handlers do lock-screen + zera o nowPlayingInfo (sub-3)
        runCatching { AVAudioSession.sharedInstance().setActive(false, null) }
        _state.update { it.copy(connected = false, isPlaying = false) }
    }

    /** Recalcula posição/duração no [state] sob demanda (sem observador periódico — ver KDoc). */
    private fun syncState() {
        _state.update {
            it.copy(
                positionMs = secondsToMs(currentPositionSeconds()),
                durationMs = secondsToMs(currentDurationSec()),
            )
        }
    }

    private fun currentScheduled(): ScheduledItem? = pipeline.firstOrNull()

    private fun currentDurationSec(): Double {
        val cur = currentScheduled() ?: return 0.0
        return framesToSeconds(cur.frames, cur.sampleRate)
    }

    /**
     * Posição na faixa atual: `sampleTime` do nó (contínuo no encadeamento) menos
     * [trackStartSampleTime], mais [seekBaseSeconds] ([trackPositionSeconds]).
     * `playerTimeForNodeTime(lastRenderTime)` pode ser null antes de tocar → só a base.
     */
    private fun currentPositionSeconds(): Double {
        val node = playerNode ?: return seekBaseSeconds
        val cur = currentScheduled() ?: return seekBaseSeconds
        if (cur.sampleRate <= 0.0) return seekBaseSeconds
        val nodeTime = node.lastRenderTime ?: return seekBaseSeconds
        val playerTime = node.playerTimeForNodeTime(nodeTime) ?: return seekBaseSeconds
        return trackPositionSeconds(playerTime.sampleTime, trackStartSampleTime, cur.sampleRate, seekBaseSeconds)
    }
}

// --- Lógica pura de frame/tempo/fila/advance (sem AVFoundation) — testável no iosSimulatorArm64Test ---
// (reusa volumeLevel/applyMuteToggle/secondsToMs/endAdvancePos/manualNextPos/buildPlayOrder/
//  buildShuffleAll/queueIdsInPlayOrder de IosAudioPlayer.kt — mesmo módulo/pacote.)

/**
 * Largura de banda (em OITAVAS) dos filtros paramétricos do `AVAudioUnitEQ` (SUB-4). ~1 oitava: as
 * 10 freqs [32..16000] são ~oitava-espaçadas. **device-gated**: o Android usa `cutoff = freq*√2` num
 * modelo `DynamicsProcessing` DIFERENTE → a largura exata NÃO mapeia 1:1 pro paramétrico do iOS.
 * Ganho/freq/`globalGain` mapeiam direto; a LARGURA é knob a CALIBRAR no device (sem Mac/iPhone aqui).
 */
internal const val EQ_BANDWIDTH_OCTAVES: Float = 1.0f

/** Segundos → frames (floor). Não-positivos ou sampleRate inválida → 0. */
internal fun secondsToFrames(sec: Double, sampleRate: Double): Long =
    if (sec <= 0.0 || sampleRate <= 0.0) 0L else (sec * sampleRate).toLong()

/** Frames → segundos. sampleRate inválida → 0. */
internal fun framesToSeconds(frames: Long, sampleRate: Double): Double =
    if (sampleRate > 0.0) frames / sampleRate else 0.0

/** fraction 0..1 × duração → segundos (clamp em 0..dur, nunca negativo). */
internal fun fractionToSeconds(frac: Float, durSec: Double): Double =
    (frac.coerceIn(0f, 1f) * durSec).coerceAtLeast(0.0)

/** Posição/duração → fração 0..1 (duração ≤0 → 0). */
internal fun positionFraction(posSec: Double, durSec: Double): Float =
    if (durSec > 0.0) (posSec / durSec).toFloat().coerceIn(0f, 1f) else 0f

/**
 * Token anti-corrida: um `completionHandler` só deve agir se o `completionGen` capturado no
 * agendamento ainda for o [currentGen] corrente. Qualquer stop+reschedule bumpa o gen → descarta.
 */
internal fun shouldAdvance(completionGen: Int, currentGen: Int): Boolean = completionGen == currentGen

/** Resultado puro de um advance no fim de faixa: novo [pos], novo [trackStartSampleTime], seekBase zerado. */
internal data class AdvanceState(
    val pos: Int,
    val trackStartSampleTime: Long,
    val seekBaseSeconds: Double,
)

/**
 * Bookkeeping PURO do advance no fim de uma faixa: `null` = parar (OFF no fim). Senão o novo [pos]
 * (via [endAdvancePos]: ONE=mesmo pos, ALL=wrap p/ 0, OFF=próximo) + [trackStartSampleTime] somado
 * dos `endedFrames` da faixa que terminou (o `sampleTime` do nó é contínuo) + seekBase zerado.
 */
internal fun advanceState(
    pos: Int,
    size: Int,
    repeat: RepeatMode,
    trackStartSampleTime: Long,
    endedFrames: Long,
): AdvanceState? {
    val np = endAdvancePos(pos, size, repeat) ?: return null
    return AdvanceState(np, trackStartSampleTime + endedFrames.coerceAtLeast(0L), 0.0)
}

/**
 * Posição na faixa atual: frames decorridos no nó desde o início da faixa
 * (`nodeSampleTime - trackStartSampleTime`, nunca negativo) em segundos, mais o offset do seek.
 */
internal fun trackPositionSeconds(
    nodeSampleTime: Long,
    trackStartSampleTime: Long,
    sampleRate: Double,
    seekBaseSeconds: Double,
): Double {
    val rel = (nodeSampleTime - trackStartSampleTime).coerceAtLeast(0L)
    return framesToSeconds(rel, sampleRate) + seekBaseSeconds
}

/** Ação do `previous()`: reiniciar a faixa atual, ou ir p/ outra posição. */
internal sealed interface PrevAction {
    data object Restart : PrevAction
    data class GoTo(val pos: Int) : PrevAction
}

/**
 * Regra pura do `previous()` (espelha o [IosAudioPlayer]): >3s na faixa → reinicia; senão vai p/ a
 * anterior; no começo com repeat ALL dá a volta p/ a última; senão reinicia. Fila vazia → reinicia.
 */
internal fun previousAction(pos: Int, size: Int, currentSec: Double, repeat: RepeatMode): PrevAction = when {
    size <= 0 -> PrevAction.Restart
    currentSec > 3.0 -> PrevAction.Restart
    pos > 0 -> PrevAction.GoTo(pos - 1)
    repeat == RepeatMode.ALL -> PrevAction.GoTo(size - 1)
    else -> PrevAction.Restart
}
