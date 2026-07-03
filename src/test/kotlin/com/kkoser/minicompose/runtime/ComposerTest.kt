package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComposerTest {
    @Test
    fun `compose builds nested containers in order`() {
        val tree = compose {
            column {
                text("One")
                row {
                    text("Left")
                    text("Right")
                }
                text("Two")
            }
        }

        val column = assertInstanceOf(UiColumn::class.java, tree)
        assertEquals(3, column.children.size)
        assertEquals(UiText("One"), column.children[0])
        assertInstanceOf(UiRow::class.java, column.children[1])
        assertEquals(UiText("Two"), column.children[2])

        val row = column.children[1] as UiRow
        assertEquals(2, row.children.size)
        assertEquals(UiText("Left"), row.children[0])
        assertEquals(UiText("Right"), row.children[1])
    }

    @Test
    fun `compose preserves button callbacks inside the built tree`() {
        var clicked = false

        val tree = compose {
            column {
                button("Press me") {
                    clicked = true
                }
            }
        }

        val column = assertInstanceOf(UiColumn::class.java, tree)
        val button = assertInstanceOf(UiButton::class.java, column.children[0])

        button.onClick.invoke()

        assertTrue(clicked)
    }
}
