package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiNode

@JvmInline
internal value class GroupAnchor(val id: Int)

internal data class KeySignature(val values: List<Any?>) {
    fun describe(): String = values.joinToString(prefix = "[", postfix = "]") { value ->
        when (value) {
            null -> "null"
            else -> value.toString()
        }
    }
}

internal enum class GroupKind {
    NODE,
    KEYED_INLINE
}

internal data class GroupRecord(
    val anchor: GroupAnchor,
    var parentAnchor: GroupAnchor?,
    var groupIndexInParent: Int?,
    var kind: GroupKind = GroupKind.NODE,
    var keySignature: KeySignature? = null,
    var visited: Boolean = false,
    val observedStates: MutableSet<MutableState<*>> = linkedSetOf(),
    var childAnchors: List<GroupAnchor> = emptyList(),
    val slots: MutableList<Any?> = mutableListOf(),
    var hasCachedOutput: Boolean = false,
    var cachedNodes: List<UiNode> = emptyList(),
    var cachedNodeClass: Class<out UiNode>? = null,
    val keyedChildAnchors: MutableMap<KeySignature, GroupAnchor> = linkedMapOf()
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

    fun createKeyedGroup(parentAnchor: GroupAnchor, keySignature: KeySignature): GroupAnchor {
        val anchor = GroupAnchor(nextGroupId++)
        records[anchor] = GroupRecord(
            anchor = anchor,
            parentAnchor = parentAnchor,
            groupIndexInParent = null,
            kind = GroupKind.KEYED_INLINE,
            keySignature = keySignature
        )
        return anchor
    }

    fun removeGroup(anchor: GroupAnchor) {
        val record = records.remove(anchor) ?: return
        record.parentAnchor?.let { parentAnchor ->
            val parent = records[parentAnchor] ?: return@let
            parent.childAnchors = parent.childAnchors.filterNot { it == anchor }
            record.keySignature?.let { keySignature ->
                parent.keyedChildAnchors.remove(keySignature)
            }
        }
    }

    fun keyedChild(parentAnchor: GroupAnchor, keySignature: KeySignature): GroupAnchor? {
        return group(parentAnchor).keyedChildAnchors[keySignature]
    }

    fun registerKeyedChild(parentAnchor: GroupAnchor, keySignature: KeySignature, anchor: GroupAnchor) {
        val parent = group(parentAnchor)
        parent.keyedChildAnchors[keySignature] = anchor
    }
}
