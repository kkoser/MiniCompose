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
        var nextGroupIndex: Int = 0,
        var nextSlotIndex: Int = 0
    )

    private val frameStack = ArrayDeque<Frame>()

    init {
        frameStack.addLast(Frame(GroupAnchor(0)))
    }

    private fun currentFrame(): Frame = frameStack.last()

    internal fun <T : UiNode> emit(
        expectedNodeClass: Class<out UiNode>,
        factory: () -> T
    ): T {
        val parentFrame = currentFrame()
        val groupIndex = parentFrame.nextGroupIndex
        parentFrame.nextGroupIndex += 1
        val groupAnchor = rootComposition?.resolveChildGroup(parentFrame.groupAnchor, groupIndex)
            ?: parentFrame.groupAnchor

        rootComposition?.shouldReuseGroup(groupAnchor, expectedNodeClass)
        rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex, forceCompose = true)
        val node = CompositionRuntime.withCurrentGroup(groupAnchor) {
            factory()
        }
        rootComposition?.finishGroup(groupAnchor, node, childAnchors = emptyList())
        parentFrame.children.add(node)
        parentFrame.childAnchors.add(groupAnchor)
        return node
    }

    internal fun <T : UiNode> emitContainer(
        expectedNodeClass: Class<out UiNode>,
        factory: (List<UiNode>) -> T,
        content: Composer.() -> Unit
    ): T {
        val parentFrame = currentFrame()
        val groupIndex = parentFrame.nextGroupIndex
        parentFrame.nextGroupIndex += 1
        val groupAnchor = rootComposition?.resolveChildGroup(parentFrame.groupAnchor, groupIndex)
            ?: parentFrame.groupAnchor

        val reusableNode = rootComposition?.shouldReuseGroup(groupAnchor, expectedNodeClass)
        if (reusableNode != null) {
            rootComposition.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)
            rootComposition.retainGroupSubtree(groupAnchor)
            @Suppress("UNCHECKED_CAST")
            val reused = reusableNode as T
            parentFrame.children.add(reused)
            parentFrame.childAnchors.add(groupAnchor)
            return reused
        }

        rootComposition?.enterGroup(groupAnchor, parentFrame.groupAnchor, groupIndex)

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
        rootComposition?.finishGroup(groupAnchor, node, childAnchors)
        parentFrame.children.add(node)
        parentFrame.childAnchors.add(groupAnchor)
        return node
    }

    internal fun <T> rememberValue(factory: () -> T): T {
        val composition = rootComposition ?: error("remember can only be called during composition")
        val frame = currentFrame()
        val value = composition.remember(frame.groupAnchor, frame.nextSlotIndex, factory)
        frame.nextSlotIndex += 1
        return value
    }

    internal fun rootChildAnchors(): List<GroupAnchor> = frameStack.first.childAnchors.toList()
}

fun compose(block: Composer.() -> UiNode): UiNode = Composer().block()

internal fun compose(rootComposition: RootComposition, block: Composer.() -> UiNode): UiNode {
    val composer = Composer(rootComposition)
    val node = composer.block()
    rootComposition.finishGroup(GroupAnchor(0), node, composer.rootChildAnchors())
    return node
}

fun Composer.text(text: String): UiText = emit(UiText::class.java) { UiText(text) }

fun Composer.button(text: String, onClick: () -> Unit): UiButton =
    emit(UiButton::class.java) { UiButton(text, onClick) }

fun Composer.column(
    spacing: Int = 0,
    content: Composer.() -> Unit
): UiColumn = emitContainer(UiColumn::class.java, { children -> UiColumn(children, spacing) }, content)

fun Composer.row(
    spacing: Int = 0,
    content: Composer.() -> Unit
): UiRow = emitContainer(UiRow::class.java, { children -> UiRow(children, spacing) }, content)

fun <T> Composer.remember(factory: () -> T): T = rememberValue(factory)
