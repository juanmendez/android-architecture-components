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

import com.android.example.github.BaseTest
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.GithubService
import com.android.example.github.api.RepoSearchResponse
import com.android.example.github.db.GithubDb
import com.android.example.github.db.RepoDao
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.TestUtil
import com.android.example.github.util.testSequence
import com.android.example.github.vo.RepoSearchResult
import com.android.example.github.vo.Resource
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import retrofit2.Response
import java.io.IOException

@RunWith(JUnit4::class)
@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@FlowPreview
class FetchNextSearchPageTaskTest : BaseTest() {
    private lateinit var service: GithubService

    private lateinit var db: GithubDb

    private lateinit var repoDao: RepoDao

    private lateinit var task: FetchNextSearchPageTask

    @Before
    fun init() {
        service = mock(GithubService::class.java)
        db = mock(GithubDb::class.java)
        repoDao = mock(RepoDao::class.java)
        `when`(db.repoDao()).thenReturn(repoDao)
        task = FetchNextSearchPageTask("foo", service, db)

        doAnswer {
            it.getArgument<Runnable>(0).run()
        }.whenever(db).runInTransaction(ArgumentMatchers.any())
    }

    @Test
    fun withoutResult() = runBlocking {
        createDbResult(1)
        val repos = TestUtil.createRepos(10, "a", "b", "c")
        val result = RepoSearchResponse(10, repos)
        val call = createCall(result, null)
        `when`(service.searchRepos("foo", 1)).thenReturn(call)

        task.run().collect {
            verify(repoDao).insertRepos(repos)
            assert(it == Resource.success(false))
        }
    }

    @Test
    fun noNextPage() = runBlocking {
        createDbResult(null)
        task.run().testSequence(
            {
                assert(it == Resource.none<Boolean>())
                verifyNoMoreInteractions(service)
            }
        )
    }

    @Test
    fun nextPageWithNull() = runBlocking {
        createDbResult(1)
        val repos = TestUtil.createRepos(10, "a", "b", "c")
        val result = RepoSearchResponse(10, repos)
        val call = createCall(result, null)

        doAnswer {
            call
        }.whenever(service).searchRepos("foo", 1)

        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run().collect {
            verify(repoDao).insertRepos(repos)
            assert(it == Resource.success(false))
        }
    }

    @Test
    fun nextPageWithMore() = runBlocking {
        createDbResult(1)
        val repos = TestUtil.createRepos(10, "a", "b", "c")
        val result = RepoSearchResponse(10, repos)
        result.nextPage = 2
        val call = createCall(result, 2)
        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run().collect {
            verify(repoDao).insertRepos(repos)
            assert(it == Resource.success(true))
        }
    }

    @Test
    fun nextPageApiError() = runBlocking {
        createDbResult(1)

        val errorResponse = Response.error<RepoSearchResponse>(
            400, ResponseBody.create(
                MediaType.parse("txt"), "bar"
            )
        )

        val call = ApiUtil.createCall(errorResponse)
        `when`(service.searchRepos("foo", 1)).thenReturn(call)

        task.run().collect {
            assert(it == Resource.error("bar", true))
        }
    }

    @Test
    fun nextPageIOError() = runBlocking {
        createDbResult(1)
        val call = flow<ApiResponse<RepoSearchResponse>> {
            throw IOException("bar")
        }

        `when`(service.searchRepos("foo", 1)).thenReturn(call)
        task.run().collect {
            assert(it == Resource.error("bar", true))
        }
    }

    private fun createDbResult(nextPage: Int?) {
        val result = RepoSearchResult(
            "foo", emptyList(),
            0, nextPage
        )
        `when`(repoDao.findSearchResult("foo")).thenReturn(result)
    }

    private fun createCall(body: RepoSearchResponse,
                           nextPage: Int?): Flow<ApiResponse<RepoSearchResponse>> {
        val headers = if (nextPage == null)
            null
        else
            Headers
                .of(
                    "link",
                    "<https://api.github.com/search/repositories?q=foo&page=" + nextPage
                        + ">; rel=\"next\""
                )
        val success = if (headers == null)
            Response.success(body)
        else
            Response.success(body, headers)

        return ApiUtil.createCall(success)
    }
}