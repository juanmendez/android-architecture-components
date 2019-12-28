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
package com.android.example.github.ui.repo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.android.example.github.repository.RepoRepository
import com.android.example.github.testing.OpenForTesting
import com.android.example.github.vo.Contributor
import com.android.example.github.vo.Repo
import com.android.example.github.vo.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OpenForTesting
@FlowPreview
@ExperimentalCoroutinesApi
class RepoViewModel @Inject constructor(
    private val repository: RepoRepository
) : ViewModel() {
    private val _repoId: MutableLiveData<RepoId> = MutableLiveData()
    val repoId: LiveData<RepoId>
        get() = _repoId

    private val _repo = MutableLiveData<Resource<Repo>>()
    val repo: LiveData<Resource<Repo>>
        get() = _repo

    private val _contributors = MutableLiveData<Resource<List<Contributor>>>()
    val contributors: LiveData<Resource<List<Contributor>>>
        get() = _contributors

    fun onCreate() {
        viewModelScope.launch {
            _repoId.asFlow().map {
                it.ifExists()
            }.flatMapMerge { input ->
                repository.loadRepo(input.owner, input.name)
            }.collect {
                _repo.value = it
            }
        }

        viewModelScope.launch {
            _repoId.asFlow().map {
                it.ifExists()
            }.flatMapMerge { input ->
                repository.loadContributors(input.owner, input.name)
            }.collect {
                _contributors.value = it
            }
        }
    }

    fun retry() {
        val owner = _repoId.value?.owner
        val name = _repoId.value?.name
        if (owner != null && name != null) {
            _repoId.value = RepoId(owner, name)
        }
    }

    fun setId(owner: String, name: String) {
        val update = RepoId(owner, name)
        if (_repoId.value == update) {
            return
        }
        _repoId.value = update
    }

    data class RepoId(val owner: String, val name: String) {
        fun ifExists() : RepoId {
            return if (owner.isBlank() || name.isBlank()) {
                RepoId("", "")
            } else {
                this
            }
        }
    }
}

