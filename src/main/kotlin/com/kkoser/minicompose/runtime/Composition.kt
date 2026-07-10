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
    val readStates: MutableSet<MutableState<*>> = linkedSetOf(),
    var childScopeKeys: List<ScopeKey> = emptyList(),
    var cachedNode: UiNode? = null,
    var cachedNodeClass: Class<out UiNode>? = null
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
    private val invalidationListeners = mutableSetOf<() -> Unit>()
    // TODO: Replace this simplified per-scope map with a more faithful slot table
    // once the runtime grows into partial/scoped recomposition.
    private val slotTable = mutableMapOf<ScopeKey, MutableList<Any?>>()
    private val scopeRecords = linkedMapOf<ScopeKey, ScopeRecord>()
    private val stateToScopes = linkedMapOf<MutableState<*>, MutableSet<ScopeKey>>()
    private val stateDebugIds = IdentityHashMap<MutableState<*>, Int>()
    private val visitedScopes = mutableSetOf<ScopeKey>()
    private val dirtyScopes = linkedSetOf<ScopeKey>()
    private val debugEvents = mutableListOf<String>()
    private var recompositionCount = 0
    private var invalidationCount = 0
    private var nextStateDebugId = 1
    private var lastTreeDump = ""
    private var lastDirtyScopeDump = ""
    private var dirty = true

    var latestTree: UiNode? = null
        private set

    fun isDirty(): Boolean = dirty

    fun addInvalidationListener(listener: () -> Unit) {
        invalidationListeners.add(listener)
    }

    fun recompose(): UiNode {
        beginPass()
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
        dirtyScopes.clear()
        return tree
    }

    fun debugSnapshot(): CompositionDebugSnapshot {
        return CompositionDebugSnapshot(
            recompositionCount = recompositionCount,
            invalidationCount = invalidationCount,
            lastTreeDump = lastTreeDump,
            scopeDump = buildScopeDump(),
            dependencyDump = buildDependencyDump(),
            dirtyScopeDump = lastDirtyScopeDump,
            events = debugEvents.toList()
        )
    }

    internal fun registerRead(state: MutableState<*>) {
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
        if (isScopeDirty(scopeKey) || record.cachedNode == null) {
            prepareScopeForRecomposition(scopeKey, record)
            debugEvents.add("scope compose ${scopeKey.describe()}")
        } else {
            debugEvents.add("scope reuse ${scopeKey.describe()}")
        }
    }

    internal fun shouldReuseScope(scopeKey: ScopeKey, expectedNodeClass: Class<out UiNode>): UiNode? {
        val record = scopeRecords[scopeKey] ?: return null
        return if (isScopeDirty(scopeKey) || record.cachedNode == null || record.cachedNodeClass != expectedNodeClass) {
            null
        } else {
            record.cachedNode
        }
    }

    internal fun finishScope(scopeKey: ScopeKey, node: UiNode, childScopeKeys: List<ScopeKey>) {
        val record = scopeRecords.getOrPut(scopeKey) {
            ScopeRecord(scopeKey, parentScopeKey = null, childIndex = null)
        }
        val hadCachedNode = record.cachedNode != null
        val previousChildScopeKeys = record.childScopeKeys
        record.cachedNode = node
        record.cachedNodeClass = node::class.java
        record.childScopeKeys = childScopeKeys
        if (hadCachedNode && previousChildScopeKeys != childScopeKeys) {
            debugEvents.add(
                "scope shape changed ${scopeKey.describe()} old=${buildScopeListDump(previousChildScopeKeys)} new=${buildScopeListDump(childScopeKeys)}"
            )
        }
        dirtyScopes.remove(scopeKey)
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

    internal fun invalidate(state: MutableState<*>) {
        markDirtyScopes(stateToScopes[state].orEmpty())

        if (dirty) {
            debugEvents.add(
                "invalidate while dirty ${stateLabel(state)} dirtyScopes=${buildScopeListDump(dirtyScopes)}"
            )
            return
        }

        dirty = true
        invalidationCount += 1
        debugEvents.add(
            "invalidate #$invalidationCount ${stateLabel(state)} dirtyScopes=${buildScopeListDump(dirtyScopes)}"
        )
        invalidationListeners.forEach { listener -> listener() }
    }

    internal fun recordUiEvent(message: String) {
        debugEvents.add(message)
    }

    private fun beginPass() {
        visitedScopes.clear()
        scopeRecords.values.forEach { record ->
            record.visited = false
        }
        if (recompositionCount == 0) {
            dirtyScopes.add(ScopeKey.root)
        }
    }

    private fun pruneUnusedScopes() {
        val removedScopes = scopeRecords.values.filterNot { it.visited }.map { it.scopeKey }
        removedScopes.forEach { removedScope ->
            val removedRecord = scopeRecords.remove(removedScope) ?: return@forEach
            removedRecord.readStates.forEach { state ->
                stateToScopes[state]?.remove(removedScope)
                if (stateToScopes[state]?.isEmpty() == true) {
                    stateToScopes.remove(state)
                    state.removeObserver(this)
                }
            }
            slotTable.remove(removedScope)
        }
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
                append(" shape=")
                append(buildScopeListDump(record.childScopeKeys))
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

    private fun buildScopeListDump(scopeKeys: Collection<ScopeKey>): String {
        if (scopeKeys.isEmpty()) {
            return "[]"
        }

        return scopeKeys.joinToString(
            prefix = "[",
            postfix = "]"
        ) { it.describe() }
    }

    private fun markDirtyScopes(affectedScopes: Collection<ScopeKey>) {
        dirtyScopes.add(ScopeKey.root)
        affectedScopes.forEach { affectedScope ->
            markDirtyScopeAndAncestors(affectedScope)
        }
        lastDirtyScopeDump = buildScopeListDump(dirtyScopes)
    }

    private fun markDirtyScopeAndAncestors(scopeKey: ScopeKey) {
        var current: ScopeKey? = scopeKey
        while (current != null) {
            dirtyScopes.add(current)
            current = scopeRecords[current]?.parentScopeKey
        }
    }

    private fun isScopeDirty(scopeKey: ScopeKey): Boolean = dirtyScopes.contains(scopeKey)

    private fun prepareScopeForRecomposition(scopeKey: ScopeKey, record: ScopeRecord) {
        if (record.readStates.isEmpty()) {
            return
        }

        record.readStates.forEach { state ->
            stateToScopes[state]?.remove(scopeKey)
            if (stateToScopes[state]?.isEmpty() == true) {
                stateToScopes.remove(state)
                state.removeObserver(this)
            }
        }
        record.readStates.clear()
    }

    private fun stateLabel(state: MutableState<*>): String {
        return "state#${stateDebugIds.getOrPut(state) { nextStateDebugId++ }}"
    }
}
