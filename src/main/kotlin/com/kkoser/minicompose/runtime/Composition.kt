package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiNode

internal data class ScopeKey(val path: List<Int>) {
    fun child(index: Int): ScopeKey = ScopeKey(path + index)

    companion object {
        val root = ScopeKey(emptyList())
    }
}

internal object CompositionRuntime {
    private val currentComposition = ThreadLocal<RootComposition?>()

    fun <T> withCurrentComposition(composition: RootComposition, block: () -> T): T {
        val previous = currentComposition.get()
        currentComposition.set(composition)
        return try {
            block()
        } finally {
            currentComposition.set(previous)
        }
    }

    fun currentComposition(): RootComposition? = currentComposition.get()
}

class RootComposition(
    private val content: Composer.() -> UiNode
) {
    private val observedStates = mutableSetOf<MutableState<*>>()
    private val invalidationListeners = mutableSetOf<() -> Unit>()
    // TODO: Replace this simplified per-scope map with a more faithful slot table
    // once the runtime grows into partial/scoped recomposition.
    private val slotTable = mutableMapOf<ScopeKey, MutableList<Any?>>()
    private val visitedScopes = mutableSetOf<ScopeKey>()
    private var dirty = true

    var latestTree: UiNode? = null
        private set

    fun isDirty(): Boolean = dirty

    fun addInvalidationListener(listener: () -> Unit) {
        invalidationListeners.add(listener)
    }

    fun recompose(): UiNode {
        beginPass()
        clearObservedStates()
        val tree = CompositionRuntime.withCurrentComposition(this) {
            compose(this, content)
        }
        latestTree = tree
        dirty = false
        pruneUnusedScopes()
        return tree
    }

    internal fun registerRead(state: MutableState<*>) {
        observedStates.add(state)
        state.addObserver(this)
    }

    internal fun markScopeVisited(scopeKey: ScopeKey) {
        visitedScopes.add(scopeKey)
    }

    internal fun <T> remember(scopeKey: ScopeKey, slotIndex: Int, factory: () -> T): T {
        val slots = slotTable.getOrPut(scopeKey) { mutableListOf() }
        if (slotIndex < slots.size) {
            @Suppress("UNCHECKED_CAST")
            return slots[slotIndex] as T
        }

        val value = factory()
        while (slots.size <= slotIndex) {
            slots.add(null)
        }
        slots[slotIndex] = value
        return value
    }

    internal fun invalidate() {
        if (dirty) {
            return
        }

        dirty = true
        invalidationListeners.forEach { listener -> listener() }
    }

    private fun clearObservedStates() {
        observedStates.forEach { state ->
            state.removeObserver(this)
        }
        observedStates.clear()
    }

    private fun beginPass() {
        visitedScopes.clear()
        markScopeVisited(ScopeKey.root)
    }

    private fun pruneUnusedScopes() {
        slotTable.keys.retainAll(visitedScopes)
    }
}
