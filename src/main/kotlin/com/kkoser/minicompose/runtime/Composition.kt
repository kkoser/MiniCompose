package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiNode

internal object CompositionRuntime {
    // TODO: Revisit this ambient current-composition mechanism later.
    // An explicit context may be clearer once the runtime grows.
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
    private var dirty = true

    var latestTree: UiNode? = null
        private set

    fun isDirty(): Boolean = dirty

    fun addInvalidationListener(listener: () -> Unit) {
        invalidationListeners.add(listener)
    }

    fun recompose(): UiNode {
        clearObservedStates()
        val tree = CompositionRuntime.withCurrentComposition(this) {
            compose(content)
        }
        latestTree = tree
        dirty = false
        return tree
    }

    internal fun registerRead(state: MutableState<*>) {
        observedStates.add(state)
        state.addObserver(this)
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
}
