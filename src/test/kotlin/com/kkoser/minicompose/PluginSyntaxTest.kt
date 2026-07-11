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
