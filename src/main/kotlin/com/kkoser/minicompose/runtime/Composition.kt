package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiNode
import com.kkoser.minicompose.ui.dumpTree
import java.util.IdentityHashMap

internal object CompositionRuntime {
    private val currentComposition = ThreadLocal<RootComposition?>()
    private val currentGroup = ThreadLocal<GroupAnchor?>()

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

    fun <T> withCurrentGroup(groupAnchor: GroupAnchor, block: () -> T): T {
        val previous = currentGroup.get()
        currentGroup.set(groupAnchor)
        return try {
            block()
        } finally {
            currentGroup.set(previous)
        }
    }

    fun currentGroup(): GroupAnchor? = currentGroup.get()
}

class RootComposition(
    private val content: Composer.() -> UiNode
) {
    private val invalidationListeners = mutableSetOf<() -> Unit>()
    private val slotTable = SlotTable()
    private val stateToGroups = linkedMapOf<MutableState<*>, MutableSet<GroupAnchor>>()
    private val stateDebugIds = IdentityHashMap<MutableState<*>, Int>()
    private val visitedGroups = mutableSetOf<GroupAnchor>()
    private val dirtyGroups = linkedSetOf<GroupAnchor>()
    private val debugEvents = mutableListOf<String>()
    private var recompositionCount = 0
    private var invalidationCount = 0
    private var nextStateDebugId = 1
    private var lastTreeDump = ""
    private var lastDirtyGroupDump = ""
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
            CompositionRuntime.withCurrentGroup(slotTable.rootAnchor) {
                enterGroup(slotTable.rootAnchor, parentAnchor = null, groupIndex = null)
                compose(this, content)
            }
        }
        latestTree = tree
        recompositionCount += 1
        lastTreeDump = tree.dumpTree()
        debugEvents.add("recompose #$recompositionCount")
        debugEvents.add(lastTreeDump)
        dirty = false
        pruneUnusedGroups()
        dirtyGroups.clear()
        return tree
    }

    fun debugSnapshot(): CompositionDebugSnapshot {
        return CompositionDebugSnapshot(
            recompositionCount = recompositionCount,
            invalidationCount = invalidationCount,
            lastTreeDump = lastTreeDump,
            groupDump = buildGroupDump(),
            dependencyDump = buildDependencyDump(),
            dirtyGroupDump = lastDirtyGroupDump,
            events = debugEvents.toList()
        )
    }

    internal fun registerRead(state: MutableState<*>) {
        state.addObserver(this)
        val groupAnchor = CompositionRuntime.currentGroup() ?: slotTable.rootAnchor
        val groupRecord = slotTable.group(groupAnchor)
        val stateLabel = stateLabel(state)
        if (groupRecord.observedStates.add(state)) {
            debugEvents.add("state read $stateLabel in ${describeGroup(groupAnchor)}")
        }
        stateToGroups.getOrPut(state) { linkedSetOf() }.add(groupAnchor)
    }

    internal fun resolveChildGroup(parentAnchor: GroupAnchor, groupIndex: Int): GroupAnchor {
        val parentRecord = slotTable.group(parentAnchor)
        val existing = parentRecord.childAnchors.getOrNull(groupIndex)
        if (existing != null && slotTable.contains(existing)) {
            val record = slotTable.group(existing)
            record.parentAnchor = parentAnchor
            record.groupIndexInParent = groupIndex
            return existing
        }

        return slotTable.createGroup(parentAnchor, groupIndex)
    }

    internal fun enterGroup(
        anchor: GroupAnchor,
        parentAnchor: GroupAnchor?,
        groupIndex: Int?,
        forceCompose: Boolean = false
    ) {
        visitedGroups.add(anchor)
        val record = slotTable.group(anchor)
        record.parentAnchor = parentAnchor
        record.groupIndexInParent = groupIndex
        record.visited = true
        if (forceCompose || isGroupDirty(anchor) || record.cachedNode == null) {
            prepareGroupForRecomposition(anchor, record)
            debugEvents.add("group compose ${describeGroup(anchor)}")
        } else {
            debugEvents.add("group reuse ${describeGroup(anchor)}")
        }
    }

    internal fun shouldReuseGroup(anchor: GroupAnchor, expectedNodeClass: Class<out UiNode>): UiNode? {
        val record = slotTable.group(anchor)
        if (record.cachedNodeClass != null && record.cachedNodeClass != expectedNodeClass) {
            resetGroup(anchor, record)
            return null
        }

        return if (isGroupDirty(anchor) || record.cachedNode == null) {
            null
        } else {
            record.cachedNode
        }
    }

    internal fun retainGroupSubtree(anchor: GroupAnchor) {
        val record = slotTable.group(anchor)
        visitedGroups.add(anchor)
        record.visited = true
        record.childAnchors.forEach { childAnchor ->
            retainGroupSubtree(childAnchor)
        }
    }

    internal fun finishGroup(anchor: GroupAnchor, node: UiNode, childAnchors: List<GroupAnchor>) {
        val record = slotTable.group(anchor)
        val hadCachedNode = record.cachedNode != null
        val previousChildAnchors = record.childAnchors
        record.cachedNode = node
        record.cachedNodeClass = node::class.java
        record.childAnchors = childAnchors
        if (hadCachedNode && previousChildAnchors != childAnchors) {
            debugEvents.add(
                "group shape changed ${describeGroup(anchor)} old=${buildGroupListDump(previousChildAnchors)} new=${buildGroupListDump(childAnchors)}"
            )
        }
        dirtyGroups.remove(anchor)
    }

    internal fun <T> remember(groupAnchor: GroupAnchor, slotIndex: Int, factory: () -> T): T {
        val group = slotTable.group(groupAnchor)
        if (slotIndex < group.slots.size) {
            @Suppress("UNCHECKED_CAST")
            return group.slots[slotIndex] as T
        }

        val value = factory()
        while (group.slots.size <= slotIndex) {
            group.slots.add(null)
        }
        group.slots[slotIndex] = value
        return value
    }

    internal fun invalidate(state: MutableState<*>) {
        markDirtyGroups(stateToGroups[state].orEmpty())

        if (dirty) {
            debugEvents.add(
                "invalidate while dirty ${stateLabel(state)} dirtyGroups=${buildGroupListDump(dirtyGroups)}"
            )
            return
        }

        dirty = true
        invalidationCount += 1
        debugEvents.add(
            "invalidate #$invalidationCount ${stateLabel(state)} dirtyGroups=${buildGroupListDump(dirtyGroups)}"
        )
        invalidationListeners.forEach { listener -> listener() }
    }

    internal fun recordUiEvent(message: String) {
        debugEvents.add(message)
    }

    private fun beginPass() {
        visitedGroups.clear()
        slotTable.groups().forEach { record ->
            record.visited = false
        }
        if (recompositionCount == 0) {
            dirtyGroups.add(slotTable.rootAnchor)
        }
    }

    private fun pruneUnusedGroups() {
        val removed = slotTable.groups()
            .filter { it.anchor != slotTable.rootAnchor && !it.visited }
            .map { it.anchor }

        removed.forEach { removeGroupSubtree(it) }
    }

    private fun removeGroupSubtree(anchor: GroupAnchor) {
        if (!slotTable.contains(anchor)) {
            return
        }

        val removedGroup = slotTable.group(anchor)
        removedGroup.childAnchors.toList().forEach { childAnchor ->
            removeGroupSubtree(childAnchor)
        }

        removedGroup.observedStates.forEach { state ->
            stateToGroups[state]?.remove(anchor)
            if (stateToGroups[state]?.isEmpty() == true) {
                stateToGroups.remove(state)
                state.removeObserver(this)
            }
        }

        dirtyGroups.remove(anchor)
        visitedGroups.remove(anchor)
        slotTable.removeGroup(anchor)
    }

    private fun resetGroup(anchor: GroupAnchor, record: GroupRecord) {
        record.childAnchors.toList().forEach { childAnchor ->
            removeGroupSubtree(childAnchor)
        }
        clearObservedStates(anchor, record)
        record.childAnchors = emptyList()
        record.slots.clear()
        record.cachedNode = null
        record.cachedNodeClass = null
    }

    private fun buildGroupDump(): String {
        val groups = slotTable.groups()
        if (groups.isEmpty()) {
            return ""
        }

        return buildString {
            groups.forEach { record ->
                append("  ")
                append(describeGroup(record.anchor))
                append(" parent=")
                append(record.parentAnchor?.let(::describeGroup) ?: "-")
                append(" child=")
                append(record.groupIndexInParent?.toString() ?: "-")
                append(" shape=")
                append(buildGroupListDump(record.childAnchors))
                append(" slots=")
                append(record.slots.size)
                append(" reads=")
                append(
                    if (record.observedStates.isEmpty()) {
                        "[]"
                    } else {
                        record.observedStates.joinToString(prefix = "[", postfix = "]") { stateLabel(it) }
                    }
                )
                appendLine()
            }
        }.trimEnd()
    }

    private fun buildDependencyDump(): String {
        if (stateToGroups.isEmpty()) {
            return ""
        }

        return buildString {
            stateToGroups.forEach { (state, groups) ->
                append("  ")
                append(stateLabel(state))
                append(" -> ")
                append(groups.joinToString(prefix = "[", postfix = "]") { describeGroup(it) })
                appendLine()
            }
        }.trimEnd()
    }

    private fun buildGroupListDump(groupAnchors: Collection<GroupAnchor>): String {
        if (groupAnchors.isEmpty()) {
            return "[]"
        }

        return groupAnchors.joinToString(prefix = "[", postfix = "]") { describeGroup(it) }
    }

    private fun markDirtyGroups(affectedGroups: Collection<GroupAnchor>) {
        dirtyGroups.add(slotTable.rootAnchor)
        affectedGroups.forEach { affectedGroup ->
            markDirtyGroupAndAncestors(affectedGroup)
        }
        lastDirtyGroupDump = buildGroupListDump(dirtyGroups)
    }

    private fun markDirtyGroupAndAncestors(anchor: GroupAnchor) {
        var current: GroupAnchor? = anchor
        while (current != null && slotTable.contains(current)) {
            dirtyGroups.add(current)
            current = slotTable.group(current).parentAnchor
        }
    }

    private fun isGroupDirty(anchor: GroupAnchor): Boolean = dirtyGroups.contains(anchor)

    private fun prepareGroupForRecomposition(anchor: GroupAnchor, record: GroupRecord) {
        clearObservedStates(anchor, record)
    }

    private fun clearObservedStates(anchor: GroupAnchor, record: GroupRecord) {
        if (record.observedStates.isEmpty()) {
            return
        }

        record.observedStates.forEach { state ->
            stateToGroups[state]?.remove(anchor)
            if (stateToGroups[state]?.isEmpty() == true) {
                stateToGroups.remove(state)
                state.removeObserver(this)
            }
        }
        record.observedStates.clear()
    }

    private fun describeGroup(anchor: GroupAnchor): String {
        if (anchor == slotTable.rootAnchor) {
            return "root"
        }

        val indices = mutableListOf<Int>()
        var current: GroupAnchor? = anchor
        while (current != null && current != slotTable.rootAnchor) {
            val record = slotTable.group(current)
            indices.add(record.groupIndexInParent ?: error("Missing group index for $current"))
            current = record.parentAnchor
        }
        return indices.asReversed().joinToString(separator = "/")
    }

    private fun stateLabel(state: MutableState<*>): String {
        return "state#${stateDebugIds.getOrPut(state) { nextStateDebugId++ }}"
    }
}
