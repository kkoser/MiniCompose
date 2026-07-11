package com.kkoser.minicompose.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ComposableRootTest {
    @Test
    fun `composableRoot accepts a single emitted root node`() {
        val composition = composableRoot {
            column {
                text("Root")
            }
        }

        val tree = composition.recompose()
        val column = assertInstanceOf(com.kkoser.minicompose.ui.UiColumn::class.java, tree)

        assertEquals(1, column.children.size)
    }

    @Test
    fun `composableRoot fails when no root nodes are emitted`() {
        val composition = composableRoot {}

        val error = assertThrows<IllegalStateException> {
            composition.recompose()
        }

        assertEquals("Expected exactly one root node, but composableRoot emitted none", error.message)
    }

    @Test
    fun `composableRoot fails when multiple root nodes are emitted`() {
        val composition = composableRoot {
            text("One")
            text("Two")
        }

        val error = assertThrows<IllegalStateException> {
            composition.recompose()
        }

        assertEquals("Expected exactly one root node, but composableRoot emitted 2", error.message)
    }
}
