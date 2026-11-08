package com.mpx.player.ui.nav

/**
 * Destinos de topo do MPX (substitui o antigo `Tab` do :app). Cinco destinos sem argumento — um
 * enum basta; só viraria `sealed interface` se algum destino precisasse carregar parâmetros, o que
 * não é o caso. `label` é o texto curto mostrado na barra/trilho de navegação.
 */
enum class Destination(val label: String) {
    PLAY("PLAY"),
    LIBRARY("LIB"),
    QUEUE("QUEUE"),
    EQ("EQ"),
    CONFIG("CFG"),
}

/**
 * Back-stack imutável e PURO (zero Android, zero dep) da navegação de topo. Topo da pilha =
 * [current]. Cada método devolve um NOVO [NavState] (reducer puro), o que o torna trivial de salvar
 * (`rememberSaveable` no :app) e de testar (commonTest → android + iOS).
 *
 * Convenção FIXADA (reordena-vs-empilha): [goTo] nunca duplica um destino — cada um aparece no
 * máximo 1x e a ordem é a de "mais-recente-visitado". Como há 5 destinos distintos, a pilha é
 * naturalmente limitada a 5 (alternar abas não a faz crescer sem fim).
 */
data class NavState(val stack: List<Destination> = listOf(Destination.PLAY)) {

    /** Destino atualmente visível (topo da pilha). */
    val current: Destination get() = stack.last()

    /** Há para onde voltar? (pilha com mais de um destino). */
    val canGoBack: Boolean get() = stack.size > 1

    /**
     * Vai para [d]: se já for o [current], no-op; senão REMOVE qualquer ocorrência de [d] e o
     * empilha no topo (dedup → cada destino no máx. 1x; ordem = mais-recente-visitado).
     */
    fun goTo(d: Destination): NavState =
        if (d == current) this else NavState(stack.filterNot { it == d } + d)

    /** Desempilha o topo (política iOS). No-op se só restar um destino; preserva a ordem. */
    fun pop(): NavState =
        if (stack.size <= 1) this else NavState(stack.dropLast(1))

    /** Reduz a pilha a `[PLAY]` (política de back do Android — A1). No-op se já for `[PLAY]`. */
    fun popToRoot(): NavState =
        if (stack.size == 1 && current == Destination.PLAY) this else NavState(listOf(Destination.PLAY))

    /** Troca o destino do topo sem crescer a pilha. No-op se [d] já for o [current]. */
    fun replace(d: Destination): NavState =
        if (d == current) this else NavState(stack.dropLast(1) + d)
}
