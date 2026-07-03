package com.kkoser.minicompose.runtime

data class CompositionDebugSnapshot(
    val recompositionCount: Int,
    val invalidationCount: Int,
    val lastTreeDump: String,
    val scopeDump: String,
    val dependencyDump: String,
    val events: List<String>
)

interface CompositionDebugObserver {
    fun onEvent(message: String)
}

object NoOpCompositionDebugObserver : CompositionDebugObserver {
    override fun onEvent(message: String) = Unit
}
