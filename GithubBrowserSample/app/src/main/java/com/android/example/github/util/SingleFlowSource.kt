package com.android.example.github.util

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.flow.Flow

class SingleFlowSource<T> {
    private val _mediator = MediatorLiveData<T>()
    val asLiveData: LiveData<T>
        get() = _mediator

    private var lastLiveData: LiveData<T>? = null

    var asFlow: Flow<T>? = null
        set(value) {
            field = value

            lastLiveData?.let {
                _mediator.removeSource(it)
            }

            value?.let { newFlow ->
                lastLiveData = newFlow.asLiveData().apply {
                    _mediator.addSource(this) {
                        _mediator.value = it
                    }
                }
            }
        }

    fun clear() {
        asFlow = null
    }
}