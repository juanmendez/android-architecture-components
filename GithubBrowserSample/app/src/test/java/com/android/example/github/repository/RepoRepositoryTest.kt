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

package com.android.example.github.repository

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.android.example.github.BaseTest
import com.android.example.github.api.GithubService
import com.android.example.github.db.GithubDb
import com.android.example.github.db.RepoDao
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.FlowChannel
import com.android.example.github.util.TestUtil
import com.android.example.github.util.testSequence
import com.android.example.github.vo.Contributor
import com.android.example.github.vo.Repo
import com.android.example.github.vo.RepoSearchResult
import com.android.example.github.vo.Resource
import com.android.example.github.vo.Status
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

@RunWith(JUnit4::class)
@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@FlowPreview
class RepoRepositoryTest : BaseTest() {
    private lateinit var repository: RepoRepository
    private val dao = mock(RepoDao::class.java)
    private val service = mock(GithubService::class.java)
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()

    @Before
    fun init() {
        val db = mock(GithubDb::class.java)
        `when`(db.repoDao()).thenReturn(dao)
        `when`(db.runInTransaction(ArgumentMatchers.any())).thenCallRealMethod()
        repository = RepoRepository(db, dao, service)
    }

    @Test
    fun loadRepoFromNetwork() = runBlocking {
        val repo = TestUtil.createRepo("foo", "bar", "desc")
        `when`(service.getRepo("foo", "bar")).thenReturn(
            ApiUtil.successCall(repo)
        )

        val dataFlow = FlowChannel<Repo?>()
        dataFlow.value = null
        `when`(dao.load("foo", "bar")).thenReturn(dataFlow.asFlow)

        repository.loadRepo("foo", "bar").collect {
            verify(service).getRepo("foo", "bar")
            verify(dao).load("foo", "bar")
            verify(dao).insert(repo)
            assert(it == Resource.loading(null))
        }
    }

    @Test
    fun loadContributors() = runBlocking {
        val dbData = FlowChannel<List<Contributor>?>()
        dbData.value = null

        whenever(dao.loadContributors("foo", "bar")).thenReturn(dbData.asFlow)

        doAnswer {
            dbData.closeWith(it.getArgument<List<Contributor>>(0))
        }.whenever(dao).insertContributors(ArgumentMatchers.anyList())

        val repo = TestUtil.createRepo("foo", "bar", "desc")
        val contributors = listOf(TestUtil.createContributor(repo, "log", 3))
        val call = ApiUtil.successCall(
            contributors
        )
        whenever(service.getContributors("foo", "bar"))
            .thenReturn(call)

        repository.loadContributors("foo", "bar").testSequence(
            {
                assert(it == Resource.loading(null))
            },
            {
                verify(service).getContributors("foo", "bar")
                verify(dao).insertContributors(contributors)
                assert(it == Resource(Status.SUCCESS, contributors, null))
            }
        )
    }

    @Test
    fun searchNextPage_null() = runBlocking {
        `when`(dao.findSearchResult("foo")).thenReturn(null)
        repository.searchNextPage("foo").collect {
            assert(it.status == Status.NONE)
        }
    }

    @Test
    fun search_fromDb() = runBlocking {
        // not passing yet...
        val ids = arrayListOf(1, 2)

        val dbResult = RepoSearchResult("foo", ids, 2, null)
        val dbSearchResult = FlowChannel<RepoSearchResult?>()
        dbSearchResult.closeWith(dbResult)
        val repositories = listOf<Repo>()

        whenever(dao.search("foo")).thenReturn(dbSearchResult.asFlow)
        whenever(dao.loadOrdered(ids)).thenReturn(repositories)


        repository.search("foo").testSequence({

            }, {
            assert(it.status == Status.SUCCESS && it.data?.isEmpty() == true)
            verifyNoMoreInteractions(service)
        })
    }

    /*
    @Test
    fun search_fromServer() = runBlocking{
        val ids = arrayListOf(1, 2)
        val repo1 = TestUtil.createRepo(1, "owner", "repo 1", "desc 1")
        val repo2 = TestUtil.createRepo(2, "owner", "repo 2", "desc 2")

        val observer = mock<FlowCollector<Resource<List<Repo>>>>()
        val dbSearchResult = ChannelFlow<RepoSearchResult>()
        val repositories = ChannelFlow<List<Repo>>()

        val repoList = arrayListOf(repo1, repo2)
        val apiResponse = RepoSearchResponse(2, repoList)

        val callLiveData = ChannelFlow<ApiResponse<RepoSearchResponse>>()
        `when`(service.searchRepos("foo")).thenReturn(callLiveData)

        `when`(dao.search("foo")).thenReturn(dbSearchResult)

        repository.search("foo").observeForever(observer)

        verify(observer).emit(Resource.loading(null))
        verifyNoMoreInteractions(service)
        reset(observer)

        `when`(dao.loadOrdered(ids)).thenReturn(repositories)
        dbSearchResult.postValue(null)
        verify(dao, never()).loadOrdered(anyList())

        verify(service).searchRepos("foo")
        val updatedResult = ChannelFlow<RepoSearchResult>()
        `when`(dao.search("foo")).thenReturn(updatedResult)
        updatedResult.postValue(RepoSearchResult("foo", ids, 2, null))

        callLiveData.postValue(ApiResponse.create(Response.success(apiResponse)))
        verify(dao).insertRepos(repoList)
        repositories.postValue(repoList)
        verify(observer).emit(Resource.success(repoList))
        verifyNoMoreInteractions(service)
    }

    @Test
    fun search_fromServer_error() = runBlocking{
        `when`(dao.search("foo")).thenReturn(AbsentLiveData.create())
        val apiResponse = ChannelFlow<ApiResponse<RepoSearchResponse>>()
        `when`(service.searchRepos("foo")).thenReturn(apiResponse)

        val observer = mock<FlowCollector<Resource<List<Repo>>>>()
        repository.search("foo").observeForever(observer)
        verify(observer).emit(Resource.loading(null))

        apiResponse.postValue(ApiResponse.create(Exception("idk")))
        verify(observer).emit(Resource.error("idk", null))
    }*/
}