package com.android.example.github.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed

suspend fun <T> Flow<T>.testSequence(vararg callbacks: (T) -> Unit) {

    collectIndexed { i, t ->
        if (i < callbacks.size) {
            callbacks[i].invoke(t)
        } else {
            throw Error("Flow<T>.testSequence() missing an expected callback")
        }
    }
}