package com.android.example.github.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class TestFlow<T>(): Flow<T> {
    private var flowCollector: FlowCollector<T>? = null
    private val pending = mutableListOf<T>()

    constructor(initialFlow: T) : this() {
        pending.add(initialFlow)
    }

    suspend fun offer(value: T) {
        if(flowCollector == null) {
            pending.add(value)
        } else {
            flowCollector?.emit(value)
        }
    }

    @InternalCoroutinesApi
    override suspend fun collect(collector: FlowCollector<T>) {
        flowCollector = collector

        pending.forEach {
            collector.emit(it)
        }
        pending.clear()
    }
}

class TestCollector<T> {
    private val values = mutableListOf<T>()

    fun test(scope: CoroutineScope, flow: Flow<T>): Job {
        return scope.launch { flow.collect { values.add(it) } }
    }

    fun assertValues(vararg _values: T): Boolean {
        return values == _values
    }

    fun lastValue(): T? = values.lastOrNull()
}