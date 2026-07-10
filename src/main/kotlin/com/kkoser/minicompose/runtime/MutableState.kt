package com.kkoser.minicompose.runtime

class MutableState<T>(initialValue: T) {
    private val observers = mutableSetOf<RootComposition>()
    private var backingValue: T = initialValue

    var value: T
        get() {
            CompositionRuntime.currentComposition()?.registerRead(this)
            return backingValue
        }
        set(newValue) {
            if (backingValue == newValue) {
                return
            }

            backingValue = newValue
            observers.toList().forEach { observer ->
                observer.invalidate(this)
            }
        }

    internal fun addObserver(composition: RootComposition) {
        observers.add(composition)
    }

    internal fun removeObserver(composition: RootComposition) {
        observers.remove(composition)
    }
}

fun <T> mutableStateOf(initialValue: T): MutableState<T> = MutableState(initialValue)
