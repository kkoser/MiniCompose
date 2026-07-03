package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootCompositionTest {
    @Test
    fun `recomposition rebuilds the full tree from the latest state`() {
        val count = mutableStateOf(0)
        val composition = RootComposition {
            column {
                text("Count: ${count.value}")
            }
        }

        val initialTree = composition.recompose()

        assertEquals(UiText("Count: 0"), (initialTree as UiColumn).children[0])
        assertFalse(composition.isDirty())

        count.value = 1

        assertTrue(composition.isDirty())

        val updatedTree = composition.recompose()

        assertEquals(UiText("Count: 1"), (updatedTree as UiColumn).children[0])
        assertFalse(composition.isDirty())
    }

    @Test
    fun `debug snapshot tracks recompositions invalidations and tree dump`() {
        val count = mutableStateOf(0)
        val composition = RootComposition {
            column {
                text("Count: ${count.value}")
            }
        }

        composition.recompose()
        count.value = 1
        composition.recompose()

        val snapshot = composition.debugSnapshot()

        assertEquals(2, snapshot.recompositionCount)
        assertEquals(1, snapshot.invalidationCount)
        assertEquals(
            """
            Column(spacing=0)
              Text(text="Count: 1")
            """.trimIndent(),
            snapshot.lastTreeDump
        )
        assertTrue(snapshot.events.any { it == "invalidate #1" })
        assertTrue(snapshot.events.any { it == "recompose #2" })
    }
}
