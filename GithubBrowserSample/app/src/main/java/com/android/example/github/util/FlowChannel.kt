package com.android.example.github.util

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FlowChannel<T> {
    private val channel =
        Channel<T?>(Channel.CONFLATED)

    var value: T? = null
        set(value) {
            field = value
            channel.offer(value)
        }

    fun close(throwable: Throwable? = null) {
        channel.close(throwable)
    }

    fun closeWith(lastOffer: T?, throwable: Throwable? = null) {
        value = lastOffer
        close(throwable)
    }

    val asFlow: Flow<T?>
        get() = flow {
            for (value in channel) {
                emit(value)
            }
        }
}
