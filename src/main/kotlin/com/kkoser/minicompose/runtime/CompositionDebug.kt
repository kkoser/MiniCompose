package com.kkoser.minicompose.runtime

data class CompositionDebugSnapshot(
    val recompositionCount: Int,
    val invalidationCount: Int,
    val lastTreeDump: String,
    val groupDump: String,
    val dependencyDump: String,
    val dirtyGroupDump: String,
    val events: List<String>
)

interface CompositionDebugObserver {
    fun onEvent(message: String)
}

object NoOpCompositionDebugObserver : CompositionDebugObserver {
    override fun onEvent(message: String) = Unit
}
