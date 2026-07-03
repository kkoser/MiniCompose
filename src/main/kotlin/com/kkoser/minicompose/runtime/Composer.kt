package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiNode
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import java.util.ArrayDeque

class Composer {
    private val containerStack = ArrayDeque<MutableList<UiNode>>()

    internal fun <T : UiNode> emit(node: T): T {
        containerStack.lastOrNull()?.add(node)
        return node
    }

    internal fun <T : UiNode> emitContainer(
        factory: (List<UiNode>) -> T,
        content: Composer.() -> Unit
    ): T {
        containerStack.addLast(mutableListOf())
        content()
        val children = containerStack.removeLast().toList()
        val node = factory(children)
        emit(node)
        return node
    }
}

fun compose(block: Composer.() -> UiNode): UiNode = Composer().block()

fun Composer.text(text: String): UiText = emit(UiText(text))

fun Composer.button(text: String, onClick: () -> Unit): UiButton = emit(UiButton(text, onClick))

fun Composer.column(content: Composer.() -> Unit): UiColumn = emitContainer(::UiColumn, content)

fun Composer.row(content: Composer.() -> Unit): UiRow = emitContainer(::UiRow, content)
