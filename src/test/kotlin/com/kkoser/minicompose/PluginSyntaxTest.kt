package com.kkoser.minicompose

import com.kkoser.minicompose.annotations.MiniComposable
import com.kkoser.minicompose.runtime.MutableState
import com.kkoser.minicompose.runtime.composableRoot
import com.kkoser.minicompose.runtime.mutableStateOf
import com.kkoser.minicompose.runtime.remember
import com.kkoser.minicompose.runtime.text
import com.kkoser.minicompose.runtime.column
import com.kkoser.minicompose.runtime.row
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PluginSyntaxTest {
    @Test
    fun `plugin backed composable syntax builds the same tree and keeps remember stable`() {
        val count = mutableStateOf(0)
        var firstRememberedToken: Any? = null
        var secondRememberedToken: Any? = null

        @MiniComposable
        fun app() {
            screen(count) { remembered ->
                if (count.value == 0) {
                    firstRememberedToken = remembered
                } else {
                    secondRememberedToken = remembered
                }
            }
        }

        val composition = composableRoot { app() }

        val initial = composition.recompose()
        val initialColumn = assertInstanceOf(UiColumn::class.java, initial)
        assertEquals(UiText("Count: 0"), initialColumn.children[0])

        count.value = 1

        val updated = composition.recompose()
        val updatedColumn = assertInstanceOf(UiColumn::class.java, updated)
        assertEquals(UiText("Count: 1"), updatedColumn.children[0])
        assertEquals(firstRememberedToken, secondRememberedToken)

        val row = assertInstanceOf(UiRow::class.java, updatedColumn.children[1])
        assertEquals(UiText("Label"), row.children[0])
    }

    @Test
    fun `state changes recompose the affected composable call and skip its clean sibling`() {
        val leftState = mutableStateOf(0)
        var leftExecutions = 0
        var rightExecutions = 0

        @MiniComposable
        fun left(state: MutableState<Int>) {
            leftExecutions += 1
            text("Left: ${state.value}")
        }

        @MiniComposable
        fun right() {
            rightExecutions += 1
            text("Right")
        }

        @MiniComposable
        fun app() {
            column {
                left(leftState)
                right()
            }
        }

        val composition = composableRoot { app() }
        composition.recompose()

        leftState.value = 1
        composition.recompose()

        assertEquals(2, leftExecutions)
        assertEquals(1, rightExecutions)
    }

    @Test
    fun `composable calls skip unchanged inputs and recompose changed inputs`() {
        val parentState = mutableStateOf(0)
        val childInput = mutableStateOf("First")
        var childExecutions = 0

        @MiniComposable
        fun label(value: String) {
            childExecutions += 1
            text(value)
        }

        @MiniComposable
        fun app() {
            column {
                text("Parent: ${parentState.value}")
                label(childInput.value)
            }
        }

        val composition = composableRoot { app() }
        composition.recompose()

        parentState.value = 1
        composition.recompose()
        assertEquals(1, childExecutions)

        childInput.value = "Second"
        composition.recompose()
        assertEquals(2, childExecutions)
    }

    @Test
    fun `remember is owned by a composable call group and resets after removal`() {
        val visible = mutableStateOf(true)
        var firstToken: Any? = null
        var secondToken: Any? = null

        @MiniComposable
        fun child(onToken: (Any) -> Unit) {
            onToken(remember { Any() })
            text("Child")
        }

        @MiniComposable
        fun app() {
            column {
                if (visible.value) {
                    child { token ->
                        if (firstToken == null) firstToken = token else secondToken = token
                    }
                }
            }
        }

        val composition = composableRoot { app() }
        composition.recompose()
        val initialToken = firstToken

        visible.value = false
        composition.recompose()
        visible.value = true
        composition.recompose()

        assertNotSame(initialToken, secondToken)
    }

    @Test
    fun `repeated composable callsites use call order for independent remembered values`() {
        val tick = mutableStateOf(0)
        val tokens = mutableListOf<Any>()
        var executions = 0

        @MiniComposable
        fun item(index: Int) {
            executions += 1
            val token = remember { Any() }
            tokens += token
            text("Item $index")
        }

        @MiniComposable
        fun app() {
            column {
                text("Tick: ${tick.value}")
                repeat(2) { index -> item(index) }
            }
        }

        val composition = composableRoot { app() }
        composition.recompose()
        val firstToken = tokens[0]
        val secondToken = tokens[1]

        tick.value = 1
        composition.recompose()

        assertEquals(2, executions)
        assertNotSame(firstToken, secondToken)
        assertSame(firstToken, tokens[0])
        assertSame(secondToken, tokens[1])
    }

    @Test
    fun `named composable arguments keep their source evaluation order`() {
        val evaluationOrder = mutableListOf<String>()

        fun value(label: String): String {
            evaluationOrder += label
            return label
        }

        @MiniComposable
        fun child(first: String, second: String) {
            text("$first/$second")
        }

        @MiniComposable
        fun app() {
            column {
                child(second = value("second"), first = value("first"))
            }
        }

        composableRoot { app() }.recompose()

        assertEquals(listOf("second", "first"), evaluationOrder)
    }

    @Test
    fun `a caught composable exception closes its call group`() {
        @MiniComposable
        fun failingChild() {
            error("Expected test failure")
        }

        @MiniComposable
        fun app() {
            column {
                try {
                    failingChild()
                } catch (_: IllegalStateException) {
                    text("Recovered")
                }
            }
        }

        val tree = composableRoot { app() }.recompose()
        val column = assertInstanceOf(UiColumn::class.java, tree)

        assertEquals(listOf(UiText("Recovered")), column.children)
    }

    @MiniComposable
    private fun screen(
        count: MutableState<Int>,
        onRemembered: (Any) -> Unit
    ) {
        column {
            text("Count: ${count.value}")
            val remembered = remember { Any() }
            onRemembered(remembered)
            labelRow()
        }
    }

    @MiniComposable
    private fun labelRow() {
        row {
            text("Label")
        }
    }
}
