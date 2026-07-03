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
        assertTrue(
            snapshot.scopeDump.contains("root parent=- child=- reads=[]") &&
                snapshot.scopeDump.contains("0 parent=root child=0 reads=[state#1]")
        )
        assertTrue(snapshot.dependencyDump.contains("state#1 -> [0]"))
        assertTrue(snapshot.events.any { it == "invalidate #1" })
        assertTrue(snapshot.events.any { it == "recompose #2" })
    }

    @Test
    fun `scope records and dependency index reflect sibling scopes independently`() {
        val left = mutableStateOf(0)
        val right = mutableStateOf(0)
        val composition = RootComposition {
            column {
                column {
                    text("Left: ${left.value}")
                }
                column {
                    text("Right: ${right.value}")
                }
            }
        }

        composition.recompose()

        val snapshot = composition.debugSnapshot()

        assertTrue(snapshot.scopeDump.contains("root parent=- child=- reads=[]"))
        assertTrue(snapshot.scopeDump.contains("0 parent=root child=0 reads=[]"))
        assertTrue(snapshot.scopeDump.contains("0/0 parent=0 child=0 reads=[state#1]"))
        assertTrue(snapshot.scopeDump.contains("0/1 parent=0 child=1 reads=[state#2]"))
        assertTrue(snapshot.dependencyDump.contains("state#1 -> [0/0]"))
        assertTrue(snapshot.dependencyDump.contains("state#2 -> [0/1]"))
    }

    @Test
    fun `conditional scopes are pruned and recreated across recomposition passes`() {
        val showNested = mutableStateOf(true)
        val composition = RootComposition {
            column {
                if (showNested.value) {
                    column {
                        text("Visible")
                    }
                }
            }
        }

        composition.recompose()
        assertTrue(composition.debugSnapshot().scopeDump.contains("0/0 parent=0 child=0 reads=[]"))

        showNested.value = false
        composition.recompose()
        val hiddenSnapshot = composition.debugSnapshot()
        assertTrue(hiddenSnapshot.scopeDump.contains("0 parent=root child=0 reads=[state#1]"))
        assertTrue(hiddenSnapshot.scopeDump.contains("root parent=- child=- reads=[]"))
        assertFalse(hiddenSnapshot.scopeDump.contains("0/0"))

        showNested.value = true
        composition.recompose()
        assertTrue(composition.debugSnapshot().scopeDump.contains("0/0 parent=0 child=0 reads=[]"))
    }
}
