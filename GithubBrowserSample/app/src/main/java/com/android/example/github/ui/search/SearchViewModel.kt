/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.example.github.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.android.example.github.repository.RepoRepository
import com.android.example.github.testing.OpenForTesting
import com.android.example.github.util.SingleFlowSource
import com.android.example.github.vo.Repo
import com.android.example.github.vo.Resource
import com.android.example.github.vo.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

@OpenForTesting
@ExperimentalCoroutinesApi
@FlowPreview
class SearchViewModel @Inject constructor(private val repoRepository: RepoRepository) :
    ViewModel() {

    private val _query = MediatorLiveData<String>()
    private val nextPageHandler = NextPageHandler(repoRepository, viewModelScope.coroutineContext)

    val query: LiveData<String> = _query

    private val _results = SingleFlowSource<Resource<List<Repo>>>()
    val results: LiveData<Resource<List<Repo>>>
        get() = _results.asLiveData

    val loadMoreStatus: LiveData<LoadMoreState>
        get() = nextPageHandler.loadMoreState

    init {
        onCreate()
    }

    private fun onCreate() = viewModelScope.launch {
        _query.asFlow().collect { search ->

            _results.asFlow = if (search.isNullOrBlank()) {
                flow {
                    emit(Resource.success(listOf()))
                }
            } else {
                repoRepository.search(search)
            }
        }
    }

    fun setQuery(originalInput: String) {
        val input = originalInput.toLowerCase(Locale.getDefault()).trim()
        if (input == _query.value) {
            return
        }
        nextPageHandler.reset()
        _query.value = input
    }

    fun loadNextPage() {
        _query.value?.let {
            if (it.isNotBlank()) {
                nextPageHandler.queryNextPage(it)
            }
        }
    }

    fun refresh() {
        _query.value?.let {
            _query.value = it
        }
    }

    class LoadMoreState(val isRunning: Boolean, val errorMessage: String?) {
        private var handledError = false

        val errorMessageIfNotHandled: String?
            get() {
                if (handledError) {
                    return null
                }
                handledError = true
                return errorMessage
            }
    }

    class NextPageHandler(
        private val repository: RepoRepository,
        private val context: CoroutineContext
    ) : CoroutineScope by CoroutineScope(context) {
        private var nextPageJob: Job? = null
        val loadMoreState = MutableLiveData<LoadMoreState>()
        private var query: String? = null
        private var _hasMore: Boolean = false
        val hasMore
            get() = _hasMore

        init {
            reset()
        }

        fun queryNextPage(nextQuery: String) {
            if (query == nextQuery) {
                return
            }
            unregister()
            query = nextQuery

            nextPageJob = launch {
                loadMoreState.value = LoadMoreState(
                    isRunning = true,
                    errorMessage = null
                )

                repository.searchNextPage(nextQuery).collect { resource ->
                    onChanged(resource)
                }
            }
        }

        private fun onChanged(result: Resource<Boolean>) {
            when (result.status) {
                Status.SUCCESS -> {
                    _hasMore = result.data ?: false
                    unregister()
                    loadMoreState.setValue(
                        LoadMoreState(
                            isRunning = false,
                            errorMessage = null
                        )
                    )
                }
                Status.ERROR -> {
                    _hasMore = true
                    unregister()
                    loadMoreState.setValue(
                        LoadMoreState(
                            isRunning = false,
                            errorMessage = result.message
                        )
                    )
                }

                Status.NONE -> {
                    reset()
                }

                Status.LOADING -> {
                    // ignore
                }
            }
        }

        private fun unregister() {
            nextPageJob?.cancel()
            nextPageJob = null

            if (_hasMore) {
                query = null
            }
        }

        fun reset() {
            unregister()
            _hasMore = true
            loadMoreState.value = LoadMoreState(
                isRunning = false,
                errorMessage = null
            )
        }
    }
}
