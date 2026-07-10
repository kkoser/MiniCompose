package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
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
        assertTrue(snapshot.scopeDump.contains("root parent=- child=-"))
        assertTrue(snapshot.scopeDump.contains("shape=[]"))
        assertTrue(snapshot.scopeDump.contains("0 parent=root child=0"))
        assertTrue(snapshot.scopeDump.contains("reads=[state#1]"))
        assertTrue(snapshot.dependencyDump.contains("state#1 -> [0]"))
        assertTrue(snapshot.dirtyScopeDump.contains("root"))
        assertTrue(snapshot.dirtyScopeDump.contains("0"))
        assertTrue(snapshot.events.any { it.startsWith("invalidate #1") })
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
        assertTrue(snapshot.scopeDump.contains("root parent=- child=-"))
        assertTrue(snapshot.scopeDump.contains("shape=[]"))
        assertTrue(snapshot.scopeDump.contains("0 parent=root child=0"))
        assertTrue(snapshot.scopeDump.contains("shape=[0/0, 0/1]"))
        assertTrue(snapshot.scopeDump.contains("0/0 parent=0 child=0"))
        assertTrue(snapshot.scopeDump.contains("0/1 parent=0 child=1"))
        assertTrue(snapshot.scopeDump.contains("reads=[state#1]"))
        assertTrue(snapshot.scopeDump.contains("reads=[state#2]"))
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
        assertTrue(composition.debugSnapshot().scopeDump.contains("0/0 parent=0 child=0"))

        showNested.value = false
        composition.recompose()
        val hiddenSnapshot = composition.debugSnapshot()
        assertTrue(hiddenSnapshot.scopeDump.contains("0 parent=root child=0"))
        assertTrue(hiddenSnapshot.scopeDump.contains("reads=[state#1]"))
        assertTrue(hiddenSnapshot.scopeDump.contains("root parent=- child=-"))
        assertTrue(hiddenSnapshot.scopeDump.contains("shape=[]"))
        assertFalse(hiddenSnapshot.scopeDump.contains("0/0"))
        assertTrue(hiddenSnapshot.dirtyScopeDump.contains("root"))
        assertTrue(hiddenSnapshot.dirtyScopeDump.contains("0"))

        showNested.value = true
        composition.recompose()
        assertTrue(composition.debugSnapshot().scopeDump.contains("0/0 parent=0 child=0"))
    }

    @Test
    fun `subsequent writes while already dirty keep adding dependent scopes`() {
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

        left.value = 1
        right.value = 1

        val snapshot = composition.debugSnapshot()
        assertEquals(1, snapshot.invalidationCount)
        assertTrue(snapshot.dirtyScopeDump.contains("root"))
        assertTrue(snapshot.dirtyScopeDump.contains("0/0"))
        assertTrue(snapshot.dirtyScopeDump.contains("0/1"))
        assertTrue(snapshot.events.any { it.startsWith("invalidate #1") })
        assertTrue(snapshot.events.any { it.contains("invalidate while dirty") })
    }

    @Test
    fun `unchanged sibling scopes are reused when a parent scope recomposes`() {
        val title = mutableStateOf("Initial")
        var leftFactoryCalls = 0
        var rightFactoryCalls = 0

        val composition = RootComposition {
            column {
                text("Header: ${title.value}")
                column {
                    remember {
                        leftFactoryCalls += 1
                        Any()
                    }
                    text("Left panel")
                }
                column {
                    remember {
                        rightFactoryCalls += 1
                        Any()
                    }
                    text("Right panel")
                }
            }
        }

        composition.recompose()
        title.value = "Updated"
        composition.recompose()

        val snapshot = composition.debugSnapshot()
        assertEquals(1, leftFactoryCalls)
        assertEquals(1, rightFactoryCalls)
        assertTrue(snapshot.events.count { it.startsWith("scope reuse ") } >= 2)
        assertTrue(snapshot.scopeDump.contains("0 parent=root child=0"))
        assertTrue(snapshot.scopeDump.contains("shape=[0/1, 0/2]"))
        assertTrue(snapshot.scopeDump.contains("reads=[state#1]"))
    }

    @Test
    fun `remembered values stay stable when sibling scopes are reused`() {
        val title = mutableStateOf("Initial")
        var rememberedLeft: Any? = null
        var rememberedRight: Any? = null

        val composition = RootComposition {
            column {
                text("Header: ${title.value}")
                column {
                    rememberedLeft = remember { Any() }
                    text("Left panel")
                }
                column {
                    rememberedRight = remember { Any() }
                    text("Right panel")
                }
            }
        }

        composition.recompose()
        val firstLeft = rememberedLeft
        val firstRight = rememberedRight

        title.value = "Updated"
        composition.recompose()

        assertSame(firstLeft, rememberedLeft)
        assertSame(firstRight, rememberedRight)
    }

    @Test
    fun `reused descendant scopes keep their state observers after sibling recomposition`() {
        val parentState = mutableStateOf(0)
        val childState = mutableStateOf(0)
        lateinit var childButton: UiButton

        val composition = RootComposition {
            column {
                text("Parent: ${parentState.value}")
                column {
                    text("Child: ${childState.value}")
                    childButton = button("Child +1") {
                        childState.value += 1
                    }
                }
            }
        }

        composition.recompose()
        parentState.value = 1
        composition.recompose()

        assertFalse(composition.isDirty())

        childButton.onClick.invoke()

        assertTrue(composition.isDirty())
        val updatedTree = composition.recompose()
        assertEquals(UiText("Child: 1"), (updatedTree as UiColumn).children[1].let { (it as UiColumn).children[0] })
    }
}
