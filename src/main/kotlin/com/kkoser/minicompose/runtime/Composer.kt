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
        val scopeKey: ScopeKey,
        val children: MutableList<UiNode> = mutableListOf(),
        var nextChildIndex: Int = 0,
        var nextSlotIndex: Int = 0
    )

    private val frameStack = ArrayDeque<Frame>()

    init {
        frameStack.addLast(Frame(ScopeKey.root))
        rootComposition?.markScopeVisited(ScopeKey.root)
    }

    private fun currentFrame(): Frame = frameStack.last()

    private fun reserveChildIndex(): Int {
        val frame = currentFrame()
        val childIndex = frame.nextChildIndex
        frame.nextChildIndex += 1
        return childIndex
    }

    internal fun <T : UiNode> emit(node: T): T {
        reserveChildIndex()
        currentFrame().children.add(node)
        return node
    }

    internal fun <T : UiNode> emitContainer(
        factory: (List<UiNode>) -> T,
        content: Composer.() -> Unit
    ): T {
        val parentFrame = currentFrame()
        val childIndex = parentFrame.nextChildIndex
        parentFrame.nextChildIndex += 1
        val scopeKey = parentFrame.scopeKey.child(childIndex)
        rootComposition?.markScopeVisited(scopeKey)

        frameStack.addLast(Frame(scopeKey))
        var children: List<UiNode>? = null
        try {
            content()
            children = currentFrame().children.toList()
        } finally {
            frameStack.removeLast()
        }

        val node = factory(requireNotNull(children))
        currentFrame().children.add(node)
        return node
    }

    internal fun <T> rememberValue(factory: () -> T): T {
        val composition = rootComposition ?: error("remember can only be called during composition")
        val frame = currentFrame()
        val value = composition.remember(frame.scopeKey, frame.nextSlotIndex, factory)
        frame.nextSlotIndex += 1
        return value
    }
}

fun compose(block: Composer.() -> UiNode): UiNode = Composer().block()

internal fun compose(rootComposition: RootComposition, block: Composer.() -> UiNode): UiNode =
    Composer(rootComposition).block()

fun Composer.text(text: String): UiText = emit(UiText(text))

fun Composer.button(text: String, onClick: () -> Unit): UiButton = emit(UiButton(text, onClick))

fun Composer.column(
    spacing: Int = 0,
    content: Composer.() -> Unit
): UiColumn = emitContainer({ children -> UiColumn(children, spacing) }, content)

fun Composer.row(
    spacing: Int = 0,
    content: Composer.() -> Unit
): UiRow = emitContainer({ children -> UiRow(children, spacing) }, content)

fun <T> Composer.remember(factory: () -> T): T = rememberValue(factory)
