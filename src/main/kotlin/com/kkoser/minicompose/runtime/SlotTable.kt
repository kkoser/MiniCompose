package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiNode

@JvmInline
internal value class GroupAnchor(val id: Int)

internal data class GroupRecord(
    val anchor: GroupAnchor,
    var parentAnchor: GroupAnchor?,
    var groupIndexInParent: Int?,
    var visited: Boolean = false,
    val observedStates: MutableSet<MutableState<*>> = linkedSetOf(),
    var childAnchors: List<GroupAnchor> = emptyList(),
    val slots: MutableList<Any?> = mutableListOf(),
    var cachedNode: UiNode? = null,
    var cachedNodeClass: Class<out UiNode>? = null
)

internal class SlotTable {
    private val records = linkedMapOf<GroupAnchor, GroupRecord>()
    private var nextGroupId = 1

    val rootAnchor: GroupAnchor = GroupAnchor(0)

    init {
        records[rootAnchor] = GroupRecord(
            anchor = rootAnchor,
            parentAnchor = null,
            groupIndexInParent = null
        )
    }

    fun groups(): Collection<GroupRecord> = records.values

    fun contains(anchor: GroupAnchor): Boolean = records.containsKey(anchor)

    fun group(anchor: GroupAnchor): GroupRecord = records[anchor] ?: error("Unknown group $anchor")

    fun createGroup(parentAnchor: GroupAnchor, groupIndex: Int): GroupAnchor {
        val anchor = GroupAnchor(nextGroupId++)
        records[anchor] = GroupRecord(
            anchor = anchor,
            parentAnchor = parentAnchor,
            groupIndexInParent = groupIndex
        )
        return anchor
    }

    fun removeGroup(anchor: GroupAnchor) {
        val record = records.remove(anchor) ?: return
        record.parentAnchor?.let { parentAnchor ->
            val parent = records[parentAnchor] ?: return@let
            parent.childAnchors = parent.childAnchors.filterNot { it == anchor }
        }
    }
}
