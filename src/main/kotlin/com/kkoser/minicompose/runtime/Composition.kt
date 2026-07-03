package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiNode
import com.kkoser.minicompose.ui.dumpTree
import java.util.IdentityHashMap

internal data class ScopeKey(val path: List<Int>) {
    fun child(index: Int): ScopeKey = ScopeKey(path + index)

    fun describe(): String = if (path.isEmpty()) "root" else path.joinToString(separator = "/")

    companion object {
        val root = ScopeKey(emptyList())
    }
}

internal data class ScopeRecord(
    val scopeKey: ScopeKey,
    var parentScopeKey: ScopeKey?,
    var childIndex: Int?,
    var visited: Boolean = false,
    val readStates: MutableSet<MutableState<*>> = linkedSetOf()
)

internal object CompositionRuntime {
    private val currentComposition = ThreadLocal<RootComposition?>()
    private val currentScope = ThreadLocal<ScopeKey?>()

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

    fun <T> withCurrentScope(scopeKey: ScopeKey, block: () -> T): T {
        val previous = currentScope.get()
        currentScope.set(scopeKey)
        return try {
            block()
        } finally {
            currentScope.set(previous)
        }
    }

    fun currentScope(): ScopeKey? = currentScope.get()
}

class RootComposition(
    private val content: Composer.() -> UiNode
) {
    private val observedStates = mutableSetOf<MutableState<*>>()
    private val invalidationListeners = mutableSetOf<() -> Unit>()
    // TODO: Replace this simplified per-scope map with a more faithful slot table
    // once the runtime grows into partial/scoped recomposition.
    private val slotTable = mutableMapOf<ScopeKey, MutableList<Any?>>()
    private val scopeRecords = linkedMapOf<ScopeKey, ScopeRecord>()
    private val stateToScopes = linkedMapOf<MutableState<*>, MutableSet<ScopeKey>>()
    private val stateDebugIds = IdentityHashMap<MutableState<*>, Int>()
    private val visitedScopes = mutableSetOf<ScopeKey>()
    private val debugEvents = mutableListOf<String>()
    private var recompositionCount = 0
    private var invalidationCount = 0
    private var nextStateDebugId = 1
    private var lastTreeDump = ""
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
            CompositionRuntime.withCurrentScope(ScopeKey.root) {
                enterScope(ScopeKey.root, parentScopeKey = null, childIndex = null)
                compose(this, content)
            }
        }
        latestTree = tree
        recompositionCount += 1
        lastTreeDump = tree.dumpTree()
        debugEvents.add("recompose #$recompositionCount")
        debugEvents.add(lastTreeDump)
        dirty = false
        pruneUnusedScopes()
        return tree
    }

    fun debugSnapshot(): CompositionDebugSnapshot {
        return CompositionDebugSnapshot(
            recompositionCount = recompositionCount,
            invalidationCount = invalidationCount,
            lastTreeDump = lastTreeDump,
            scopeDump = buildScopeDump(),
            dependencyDump = buildDependencyDump(),
            events = debugEvents.toList()
        )
    }

    internal fun registerRead(state: MutableState<*>) {
        observedStates.add(state)
        state.addObserver(this)
        val scopeKey = CompositionRuntime.currentScope() ?: ScopeKey.root
        val scopeRecord = scopeRecords.getOrPut(scopeKey) {
            ScopeRecord(scopeKey, parentScopeKey = null, childIndex = null)
        }
        val stateLabel = stateLabel(state)
        if (scopeRecord.readStates.add(state)) {
            debugEvents.add("state read $stateLabel in ${scopeKey.describe()}")
        }
        stateToScopes.getOrPut(state) { linkedSetOf() }.add(scopeKey)
    }

    internal fun enterScope(scopeKey: ScopeKey, parentScopeKey: ScopeKey?, childIndex: Int?) {
        visitedScopes.add(scopeKey)
        val record = scopeRecords.getOrPut(scopeKey) {
            ScopeRecord(scopeKey, parentScopeKey, childIndex)
        }
        record.parentScopeKey = parentScopeKey
        record.childIndex = childIndex
        record.visited = true
        debugEvents.add("scope enter ${scopeKey.describe()}")
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
        invalidationCount += 1
        debugEvents.add("invalidate #$invalidationCount")
        invalidationListeners.forEach { listener -> listener() }
    }

    internal fun recordUiEvent(message: String) {
        debugEvents.add(message)
    }

    private fun clearObservedStates() {
        observedStates.forEach { state ->
            state.removeObserver(this)
        }
        observedStates.clear()
    }

    private fun beginPass() {
        scopeRecords.clear()
        stateToScopes.clear()
        visitedScopes.clear()
    }

    private fun pruneUnusedScopes() {
        slotTable.keys.retainAll(visitedScopes)
    }

    private fun buildScopeDump(): String {
        if (scopeRecords.isEmpty()) {
            return ""
        }

        return buildString {
            scopeRecords.values.forEach { record ->
                append("  ")
                append(record.scopeKey.describe())
                append(" parent=")
                append(record.parentScopeKey?.describe() ?: "-")
                append(" child=")
                append(record.childIndex?.toString() ?: "-")
                append(" reads=")
                append(
                    if (record.readStates.isEmpty()) {
                        "[]"
                    } else {
                        record.readStates.joinToString(
                            prefix = "[",
                            postfix = "]"
                        ) { stateLabel(it) }
                    }
                )
                appendLine()
            }
        }.trimEnd()
    }

    private fun buildDependencyDump(): String {
        if (stateToScopes.isEmpty()) {
            return ""
        }

        return buildString {
            stateToScopes.forEach { (state, scopes) ->
                append("  ")
                append(stateLabel(state))
                append(" -> ")
                append(
                    scopes.joinToString(
                        prefix = "[",
                        postfix = "]"
                    ) { it.describe() }
                )
                appendLine()
            }
        }.trimEnd()
    }

    private fun stateLabel(state: MutableState<*>): String {
        return "state#${stateDebugIds.getOrPut(state) { nextStateDebugId++ }}"
    }
}
