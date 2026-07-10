package com.kkoser.minicompose.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class RememberTest {
    @Test
    fun `remember returns the same value across recomposition for a stable scope`() {
        val count = mutableStateOf(0)
        var factoryCalls = 0
        var firstRememberedValue: Any? = null
        var secondRememberedValue: Any? = null

        val composition = RootComposition {
            column {
                val remembered = remember {
                    factoryCalls += 1
                    Any()
                }
                if (count.value == 0) {
                    firstRememberedValue = remembered
                } else {
                    secondRememberedValue = remembered
                }
                text("Count: ${count.value}")
            }
        }

        composition.recompose()
        count.value = 1
        composition.recompose()

        assertEquals(1, factoryCalls)
        assertSame(firstRememberedValue, secondRememberedValue)
    }

    @Test
    fun `remember keeps independent values in nested scopes`() {
        val version = mutableStateOf(0)
        var outerFactoryCalls = 0
        var innerFactoryCalls = 0
        var firstOuterValue: Any? = null
        var secondOuterValue: Any? = null
        var firstInnerValue: Any? = null
        var secondInnerValue: Any? = null

        val composition = RootComposition {
            column {
                text("Version: ${version.value}")
                val outer = remember {
                    outerFactoryCalls += 1
                    Any()
                }
                row {
                    val inner = remember {
                        innerFactoryCalls += 1
                        Any()
                    }
                    if (version.value == 0) {
                        firstOuterValue = outer
                        firstInnerValue = inner
                    } else {
                        secondOuterValue = outer
                        secondInnerValue = inner
                    }
                    text("Nested")
                }
            }
        }

        composition.recompose()
        version.value = 1
        composition.recompose()

        assertEquals(1, outerFactoryCalls)
        assertEquals(1, innerFactoryCalls)
        assertSame(firstOuterValue, secondOuterValue)
        assertSame(firstInnerValue, secondInnerValue)
        assertNotSame(firstOuterValue, firstInnerValue)
    }

    @Test
    fun `forgetting a scope creates a fresh remembered value when it returns`() {
        val show = mutableStateOf(true)
        var factoryCalls = 0
        var firstValue: Any? = null
        var secondValue: Any? = null

        val composition = RootComposition {
            column {
                if (show.value) {
                    column {
                        val remembered = remember {
                            factoryCalls += 1
                            Any()
                        }
                        if (firstValue == null) {
                            firstValue = remembered
                        } else {
                            secondValue = remembered
                        }
                        text("Visible")
                    }
                } else {
                    text("Hidden")
                }
            }
        }

        composition.recompose()
        show.value = false
        composition.recompose()
        show.value = true
        composition.recompose()

        assertEquals(2, factoryCalls)
        assertNotSame(firstValue, secondValue)
    }
}
