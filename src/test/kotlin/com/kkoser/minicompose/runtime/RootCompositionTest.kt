package com.kkoser.minicompose.runtime

import com.kkoser.minicompose.ui.UiButton
import com.kkoser.minicompose.ui.UiColumn
import com.kkoser.minicompose.ui.UiRow
import com.kkoser.minicompose.ui.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RootCompositionTest {
    @Test
    fun `reusable node groups compare every node input`() {
        val parentTick = mutableStateOf(0)
        val spacing = mutableStateOf(0)
        val rowSpacing = mutableStateOf(0)
        val dynamicText = mutableStateOf("Initial")
        val buttonLabel = mutableStateOf("Initial action")
        var callback: () -> Unit = {}

        val composition = RootComposition {
            column {
                text("Parent: ${parentTick.value}")
                column(spacing = spacing.value) {
                    text("Nested")
                }
                row(spacing = rowSpacing.value) {
                    text("Row child")
                }
                text(dynamicText.value)
                button(buttonLabel.value, callback)
            }
        }

        val initial = composition.recompose() as UiColumn
        val initialNested = initial.children[1] as UiColumn
        val initialRow = initial.children[2] as UiRow
        val initialText = initial.children[3] as UiText
        val initialButton = initial.children[4] as UiButton

        parentTick.value = 1
        val unchangedInputs = composition.recompose() as UiColumn
        assertSame(initialNested, unchangedInputs.children[1])
        assertSame(initialRow, unchangedInputs.children[2])
        assertSame(initialText, unchangedInputs.children[3])
        assertSame(initialButton, unchangedInputs.children[4])

        spacing.value = 8
        val changedSpacing = composition.recompose() as UiColumn
        val updatedNested = changedSpacing.children[1] as UiColumn
        assertNotSame(initialNested, updatedNested)
        assertEquals(8, updatedNested.spacing)

        rowSpacing.value = 6
        val changedRowSpacing = composition.recompose() as UiColumn
        val updatedRow = changedRowSpacing.children[2] as UiRow
        assertNotSame(initialRow, updatedRow)
        assertEquals(6, updatedRow.spacing)

        dynamicText.value = "Updated"
        val changedText = composition.recompose() as UiColumn
        val updatedText = changedText.children[3] as UiText
        assertNotSame(initialText, updatedText)
        assertEquals("Updated", updatedText.text)

        buttonLabel.value = "Updated action"
        val changedLabel = composition.recompose() as UiColumn
        val updatedButton = changedLabel.children[4] as UiButton
        assertNotSame(initialButton, updatedButton)
        assertEquals("Updated action", updatedButton.text)

        callback = {}
        parentTick.value = 2
        val changedCallback = composition.recompose() as UiColumn
        assertNotSame(updatedButton, changedCallback.children[4])
    }

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
        assertTrue(snapshot.groupDump.contains("root parent=- child=-"))
        assertTrue(snapshot.groupDump.contains("shape=[0]"))
        assertTrue(snapshot.groupDump.contains("0 parent=root child=0"))
        assertTrue(snapshot.groupDump.contains("slots=0"))
        assertTrue(snapshot.groupDump.contains("reads=[state#1]"))
        assertTrue(snapshot.dependencyDump.contains("state#1 -> [0]"))
        assertTrue(snapshot.dirtyGroupDump.contains("root"))
        assertTrue(snapshot.dirtyGroupDump.contains("0"))
        assertTrue(snapshot.events.any { it.startsWith("invalidate #1") })
        assertTrue(snapshot.events.any { it == "recompose #2" })
    }

    @Test
    fun `group records and dependency index reflect sibling groups independently`() {
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
        assertTrue(snapshot.groupDump.contains("root parent=- child=-"))
        assertTrue(snapshot.groupDump.contains("shape=[0]"))
        assertTrue(snapshot.groupDump.contains("0 parent=root child=0"))
        assertTrue(snapshot.groupDump.contains("shape=[0/0, 0/1]"))
        assertTrue(snapshot.groupDump.contains("0/0 parent=0 child=0"))
        assertTrue(snapshot.groupDump.contains("0/1 parent=0 child=1"))
        assertTrue(snapshot.groupDump.contains("reads=[state#1]"))
        assertTrue(snapshot.groupDump.contains("reads=[state#2]"))
        assertTrue(snapshot.dependencyDump.contains("state#1 -> [0/0]"))
        assertTrue(snapshot.dependencyDump.contains("state#2 -> [0/1]"))
    }

    @Test
    fun `conditional groups are pruned and recreated across recomposition passes`() {
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
        assertTrue(composition.debugSnapshot().groupDump.contains("0/0 parent=0 child=0"))

        showNested.value = false
        composition.recompose()
        val hiddenSnapshot = composition.debugSnapshot()
        assertTrue(hiddenSnapshot.groupDump.contains("0 parent=root child=0"))
        assertTrue(hiddenSnapshot.groupDump.contains("reads=[state#1]"))
        assertTrue(hiddenSnapshot.groupDump.contains("root parent=- child=-"))
        assertTrue(hiddenSnapshot.groupDump.contains("shape=[0]"))
        assertFalse(hiddenSnapshot.groupDump.contains("0/0"))
        assertTrue(hiddenSnapshot.dirtyGroupDump.contains("root"))
        assertTrue(hiddenSnapshot.dirtyGroupDump.contains("0"))

        showNested.value = true
        composition.recompose()
        assertTrue(composition.debugSnapshot().groupDump.contains("0/0 parent=0 child=0"))
    }

    @Test
    fun `subsequent writes while already dirty keep adding dependent groups`() {
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
        assertTrue(snapshot.dirtyGroupDump.contains("root"))
        assertTrue(snapshot.dirtyGroupDump.contains("0"))
        assertTrue(snapshot.dirtyGroupDump.contains("0/0"))
        assertTrue(snapshot.dirtyGroupDump.contains("0/1"))
        assertTrue(snapshot.events.any { it.startsWith("invalidate #1") })
        assertTrue(snapshot.events.any { it.contains("invalidate while dirty") })
    }

    @Test
    fun `unchanged sibling groups are reused when a parent group recomposes`() {
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
        assertTrue(snapshot.events.count { it.startsWith("group reuse ") } >= 2)
        assertTrue(snapshot.groupDump.contains("0 parent=root child=0"))
        assertTrue(snapshot.groupDump.contains("shape=[0/0, 0/1, 0/2]"))
        assertTrue(snapshot.groupDump.contains("reads=[state#1]"))
    }

    @Test
    fun `remembered values stay stable when sibling groups are reused`() {
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
    fun `key preserves remembered values when keyed siblings reorder under the same parent`() {
        val reversed = mutableStateOf(false)
        var alphaRemembered: Any? = null
        var betaRemembered: Any? = null

        val composition = RootComposition {
            column {
                if (reversed.value) {
                    key("beta") {
                        column {
                            betaRemembered = remember { Any() }
                            text("Beta")
                        }
                    }
                    key("alpha") {
                        column {
                            alphaRemembered = remember { Any() }
                            text("Alpha")
                        }
                    }
                } else {
                    key("alpha") {
                        column {
                            alphaRemembered = remember { Any() }
                            text("Alpha")
                        }
                    }
                    key("beta") {
                        column {
                            betaRemembered = remember { Any() }
                            text("Beta")
                        }
                    }
                }
            }
        }

        composition.recompose()
        val firstAlpha = alphaRemembered
        val firstBeta = betaRemembered

        reversed.value = true
        val updatedTree = composition.recompose()

        val updatedColumn = updatedTree as UiColumn
        assertEquals("Beta", ((updatedColumn.children[0] as UiColumn).children[0] as UiText).text)
        assertEquals("Alpha", ((updatedColumn.children[1] as UiColumn).children[0] as UiText).text)
        assertSame(firstAlpha, alphaRemembered)
        assertSame(firstBeta, betaRemembered)
    }

    @Test
    fun `changing a key recreates only that keyed group`() {
        val activeKey = mutableStateOf("alpha")
        var remembered: Any? = null

        val composition = RootComposition {
            column {
                key(activeKey.value) {
                    column {
                        remembered = remember { Any() }
                        text("Keyed")
                    }
                }
            }
        }

        composition.recompose()
        val first = remembered

        activeKey.value = "beta"
        composition.recompose()

        assertNotSame(first, remembered)
    }

    @Test
    fun `duplicate keys in the same keyed region fail fast`() {
        val composition = RootComposition {
            column {
                key("dup") {
                    text("First")
                }
                key("dup") {
                    text("Second")
                }
            }
        }

        assertThrows<IllegalStateException> {
            composition.recompose()
        }
    }

    @Test
    fun `changing a group node type recreates only the affected group`() {
        val swapped = mutableStateOf(false)
        var leftRemembered: Any? = null
        var rightRemembered: Any? = null

        val composition = RootComposition {
            column {
                if (swapped.value) {
                    row {
                        leftRemembered = remember { Any() }
                        text("Changed")
                    }
                } else {
                    column {
                        leftRemembered = remember { Any() }
                        text("Changed")
                    }
                }
                column {
                    rightRemembered = remember { Any() }
                    text("Stable")
                }
            }
        }

        composition.recompose()
        val firstLeft = leftRemembered
        val firstRight = rightRemembered

        swapped.value = true
        composition.recompose()

        assertNotSame(firstLeft, leftRemembered)
        assertSame(firstRight, rightRemembered)
    }

    @Test
    fun `replacing a group node type removes stale state observers from the old branch`() {
        val swapped = mutableStateOf(false)
        val oldBranchState = mutableStateOf(0)
        val stableState = mutableStateOf(0)

        val composition = RootComposition {
            column {
                if (swapped.value) {
                    row {
                        text("New branch")
                    }
                } else {
                    column {
                        text("Old: ${oldBranchState.value}")
                    }
                }
                text("Stable: ${stableState.value}")
            }
        }

        composition.recompose()

        swapped.value = true
        composition.recompose()
        assertFalse(composition.isDirty())

        oldBranchState.value = 1
        assertFalse(composition.isDirty())

        stableState.value = 1
        assertTrue(composition.isDirty())
    }

    @Test
    fun `reused descendant groups keep their state observers after sibling recomposition`() {
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
