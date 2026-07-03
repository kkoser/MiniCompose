package com.kkoser.minicompose.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MutableStateTest {
    @Test
    fun `writing a different value invalidates the active composition`() {
        val count = mutableStateOf(0)
        val composition = RootComposition {
            text("Count: ${count.value}")
        }

        assertTrue(composition.isDirty())
        composition.recompose()
        assertFalse(composition.isDirty())

        count.value = 1

        assertTrue(composition.isDirty())
    }

    @Test
    fun `writing the same value does not invalidate the composition`() {
        val count = mutableStateOf(0)
        val composition = RootComposition {
            text("Count: ${count.value}")
        }

        composition.recompose()

        count.value = 0

        assertFalse(composition.isDirty())
    }
}
