package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiNode
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import java.util.ArrayDeque

class Composer(
    private val rootComposition: RootComposition? = null
) {
    private data class Frame(
        val groupAnchor: GroupAnchor,
        val children: MutableList<UiNode> = mutableListOf(),
        val childAnchors: MutableList<GroupAnchor> = mutableListOf(),
        val seenKeySignatures: MutableSet<KeySignature> = linkedSetOf(),
        var nextGroupIndex: Int = 0,
        var nextSlotIndex: Int = 0
    )

    private val frameStack = ArrayDeque<Frame>()

    private data class ActiveComposableCall(
        val groupAnchor: GroupAnchor,
        val parentFrame: Frame,
        val inputs: List<Any?>,
        val previousRuntimeGroup: GroupAnchor?
    )

    private val activeComposableCalls = ArrayDeque<ActiveComposableCall>()

    init {
        frameStack.addLast(Frame(GroupAnchor(0)))
    }

    private fun currentFrame(): Frame = frameStack.last()

    internal fun <T : UiNode> emit(
        expectedNodeClass: Class<out UiNode>,
        inputs: List<Any?>,
        factory: () -> T
    ): T {
        val parentFrame = currentFrame()
        val groupIndex = parentFrame.nextGroupIndex
        parentFrame.nextGroupIndex += 1
        val groupAnchor = rootComposition?.resolveChildGroup(parentFrame.groupAnchor, groupIndex)
            ?: parentFrame.groupAnchor

        val reusableNode = rootComposition?.shouldReuseNodeGroup(groupAnchor, expectedNodeClass, inputs)
        if (reusableNode != null) {
            rootComposition.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)
            rootComposition.retainGroupSubtree(groupAnchor)
            @Suppress("UNCHECKED_CAST")
            val reused = reusableNode as T
            parentFrame.children.add(reused)
            parentFrame.childAnchors.add(groupAnchor)
            return reused
        }

        rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex, forceCompose = true)
        val node = CompositionRuntime.withCurrentGroup(groupAnchor) {
            factory()
        }
        rootComposition?.finishGroup(groupAnchor, listOf(node), childAnchors = emptyList(), inputSignature = inputs)
        parentFrame.children.add(node)
        parentFrame.childAnchors.add(groupAnchor)
        return node
    }

    internal fun <T : UiNode> emitContainer(
        expectedNodeClass: Class<out UiNode>,
        inputs: List<Any?>,
        factory: (List<UiNode>) -> T,
        content: Composer.() -> Unit
    ): T {
        val parentFrame = currentFrame()
        val groupIndex = parentFrame.nextGroupIndex
        parentFrame.nextGroupIndex += 1
        val groupAnchor = rootComposition?.resolveChildGroup(parentFrame.groupAnchor, groupIndex)
            ?: parentFrame.groupAnchor

        val reusableNode = rootComposition?.shouldReuseNodeGroup(groupAnchor, expectedNodeClass, inputs)
        if (reusableNode != null) {
            rootComposition.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)
            rootComposition.retainGroupSubtree(groupAnchor)
            @Suppress("UNCHECKED_CAST")
            val reused = reusableNode as T
            parentFrame.children.add(reused)
            parentFrame.childAnchors.add(groupAnchor)
            return reused
        }

        rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex, forceCompose = true)

        frameStack.addLast(Frame(groupAnchor))
        val children: List<UiNode>
        val childAnchors: List<GroupAnchor>
        try {
            CompositionRuntime.withCurrentGroup(groupAnchor) {
                content()
            }
            val frame = currentFrame()
            children = frame.children.toList()
            childAnchors = frame.childAnchors.toList()
        } finally {
            frameStack.removeLast()
        }

        val node = factory(children)
        rootComposition?.finishGroup(groupAnchor, listOf(node), childAnchors, inputs)
        parentFrame.children.add(node)
        parentFrame.childAnchors.add(groupAnchor)
        return node
    }

    internal fun key(vararg keys: Any?, content: Composer.() -> Unit) {
        val parentFrame = currentFrame()
        val groupIndex = parentFrame.nextGroupIndex
        parentFrame.nextGroupIndex += 1
        val keySignature = KeySignature(keys.toList())
        if (!parentFrame.seenKeySignatures.add(keySignature)) {
            error("Duplicate key ${keySignature.describe()} in the same keyed sibling region")
        }

        val groupAnchor = rootComposition?.resolveKeyedGroup(parentFrame.groupAnchor, keySignature)
            ?: parentFrame.groupAnchor

        val reusableNodes = rootComposition?.shouldReuseKeyedGroup(groupAnchor)
        if (reusableNodes != null) {
            rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)
            rootComposition?.retainGroupSubtree(groupAnchor)
            parentFrame.children.addAll(reusableNodes)
            parentFrame.childAnchors.add(groupAnchor)
            return
        }

        rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)

        frameStack.addLast(Frame(groupAnchor))
        val childOutputs: List<UiNode>
        val childAnchors: List<GroupAnchor>
        try {
            CompositionRuntime.withCurrentGroup(groupAnchor) {
                content()
            }
            val frame = currentFrame()
            childOutputs = frame.children.toList()
            childAnchors = frame.childAnchors.toList()
        } finally {
            frameStack.removeLast()
        }

        rootComposition?.finishGroup(groupAnchor, childOutputs, childAnchors)
        parentFrame.children.addAll(childOutputs)
        parentFrame.childAnchors.add(groupAnchor)
    }

    internal fun beginComposableCall(inputs: List<Any?>): Boolean {
        val parentFrame = currentFrame()
        val groupIndex = parentFrame.nextGroupIndex
        parentFrame.nextGroupIndex += 1
        val groupAnchor = rootComposition?.resolveChildGroup(
            parentFrame.groupAnchor,
            groupIndex,
            GroupKind.COMPOSABLE_CALL
        ) ?: parentFrame.groupAnchor

        if (rootComposition?.shouldReuseComposableCallGroup(groupAnchor, inputs) == true) {
            rootComposition.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)
            rootComposition.retainGroupSubtree(groupAnchor)
            val nodes = requireNotNull(rootComposition).cachedNodes(groupAnchor)
            parentFrame.children.addAll(nodes)
            parentFrame.childAnchors.add(groupAnchor)
            return false
        }

        rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex, forceCompose = true)
        frameStack.addLast(Frame(groupAnchor))
        activeComposableCalls.addLast(
            ActiveComposableCall(
                groupAnchor = groupAnchor,
                parentFrame = parentFrame,
                inputs = inputs,
                previousRuntimeGroup = CompositionRuntime.replaceCurrentGroup(groupAnchor)
            )
        )
        return true
    }

    internal fun endComposableCall() {
        check(activeComposableCalls.isNotEmpty()) { "No composable call is active" }
        val activeCall = activeComposableCalls.removeLast()
        val childOutputs: List<UiNode>
        val childAnchors: List<GroupAnchor>
        try {
            val frame = currentFrame()
            childOutputs = frame.children.toList()
            childAnchors = frame.childAnchors.toList()
        } finally {
            frameStack.removeLast()
            CompositionRuntime.restoreCurrentGroup(activeCall.previousRuntimeGroup)
        }

        rootComposition?.finishGroup(activeCall.groupAnchor, childOutputs, childAnchors, activeCall.inputs)
        activeCall.parentFrame.children.addAll(childOutputs)
        activeCall.parentFrame.childAnchors.add(activeCall.groupAnchor)
    }

    internal fun <T> rememberValue(factory: () -> T): T {
        val composition = rootComposition ?: error("remember can only be called during composition")
        val frame = currentFrame()
        val value = composition.remember(frame.groupAnchor, frame.nextSlotIndex, factory)
        frame.nextSlotIndex += 1
        return value
    }

    internal fun rootChildAnchors(): List<GroupAnchor> = frameStack.first.childAnchors.toList()

    internal fun requireSingleRootNode(): UiNode {
        val rootChildren = frameStack.first.children
        return when (rootChildren.size) {
            1 -> rootChildren.single()
            0 -> error("Expected exactly one root node, but composableRoot emitted none")
            else -> error("Expected exactly one root node, but composableRoot emitted ${rootChildren.size}")
        }
    }
}

fun compose(block: Composer.() -> UiNode): UiNode {
    val composer = Composer()
    return CompositionRuntime.withCurrentComposer(composer) {
        composer.block()
    }
}

internal fun compose(rootComposition: RootComposition, block: Composer.() -> UiNode): UiNode {
    val composer = Composer(rootComposition)
    val node = CompositionRuntime.withCurrentComposer(composer) {
        composer.block()
    }
    rootComposition.finishGroup(GroupAnchor(0), listOf(node), composer.rootChildAnchors())
    return node
}

fun Composer.text(text: String): UiText = emit(UiText::class.java, listOf(text)) { UiText(text) }

fun Composer.button(text: String, onClick: () -> Unit): UiButton =
    emit(UiButton::class.java, listOf(text, onClick)) { UiButton(text, onClick) }

fun Composer.column(
    spacing: Int = 0,
    content: Composer.() -> Unit
): UiColumn = emitContainer(UiColumn::class.java, listOf(spacing), { children -> UiColumn(children, spacing) }, content)

fun Composer.row(
    spacing: Int = 0,
    content: Composer.() -> Unit
): UiRow = emitContainer(UiRow::class.java, listOf(spacing), { children -> UiRow(children, spacing) }, content)

fun <T> Composer.remember(factory: () -> T): T = rememberValue(factory)
