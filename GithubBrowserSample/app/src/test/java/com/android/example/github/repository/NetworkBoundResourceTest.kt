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
import com.android.example.github.util.ApiUtil
import com.android.example.github.util.TestFlow
import com.android.example.github.util.testSequence
import com.android.example.github.vo.Status
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@FlowPreview
class NetworkBoundResourceTest: BaseTest() {

    private lateinit var handleSaveCallResult: suspend (Foo) -> Unit

    private lateinit var handleShouldMatch: (Foo?) -> Boolean

    private lateinit var handleCreateCall: () -> Flow<ApiResponse<Foo>>

    private val dbData = TestFlow<Foo?>()

    private lateinit var networkBoundResource: NetworkBoundResource<Foo, Foo>

    private val fetchedOnce = AtomicBoolean(false)

    @Before
    fun init() {
        networkBoundResource = object : NetworkBoundResource<Foo, Foo>() {
            override suspend fun saveCallResult(item: Foo) {
                handleSaveCallResult(item)
            }

            override fun shouldFetch(data: Foo?): Boolean {
                // since test methods don't handle repetitive fetching, call it only once
                return handleShouldMatch(data) && fetchedOnce.compareAndSet(false, true)
            }

            override fun loadFromDb(): Flow<Foo?> {
                return dbData
            }

            override suspend fun createCall(): Flow<ApiResponse<Foo>> {
                return handleCreateCall()
            }
        }
    }

    @Test
    fun basicFromNetwork() = runBlocking {
        val saved = AtomicReference<Foo>()
        handleShouldMatch = { it == null }

        val networkResult = Foo(1)
        handleCreateCall = { ApiUtil.createCall(Response.success(networkResult)) }

        handleSaveCallResult = { foo ->
            saved.set(foo)
            dbData.offer(foo)
        }


        dbData.offer(null)
        networkBoundResource.asFlow().testSequence(
            {
                assert(it.status == Status.LOADING)
            }, {
                assert(saved.get() == networkResult)
                assert(it.status == Status.SUCCESS)
            }
        )
    }

    @Test
    fun failureFromNetwork() = runBlocking {
        val saved = AtomicBoolean(false)
        handleShouldMatch = { it == null }
        handleSaveCallResult = { foo ->
            saved.set(true)
            dbData.offer(foo)
        }
        val errorMessage = "error"
        val body = ResponseBody.create(MediaType.parse("text/html"), errorMessage)
        handleCreateCall = { ApiUtil.createCall(Response.error<Foo>(500, body)) }

        dbData.offer(null)

        networkBoundResource.asFlow().testSequence(
            {
                assert(it.status == Status.LOADING)
            }, {
                assert(it.status == Status.ERROR)
                assert(it.message == errorMessage)
                assert(!saved.get())
            }
        )
    }

    @Test
    fun dbSuccessWithoutNetwork() = runBlocking {
        val saved = AtomicBoolean(false)
        handleShouldMatch = { it == null }
        handleSaveCallResult = {
            saved.set(true)
            dbData.offer(it)
        }

        dbData.offer(Foo(1))
        dbData.offer(Foo(2))

        networkBoundResource.asFlow().testSequence(
            {
                assert(it.status == Status.SUCCESS)
                assert(it.data == Foo(1))
                assert(!saved.get())
            }, {
                assert(it.status == Status.SUCCESS)
                assert(it.data == Foo(2))
                assert(!saved.get())
            }
        )
    }

    @Test
    fun dbSuccessWithFetchFailure() = runBlocking {
        val dbValue = Foo(1)
        val saved = AtomicBoolean(false)
        handleShouldMatch = { foo -> foo === dbValue }
        handleSaveCallResult = {
            saved.set(true)
        }

        val apiResponseLiveData = TestFlow<ApiResponse<Foo>>()
        val body = ResponseBody.create(MediaType.parse("text/html"), "error")
        apiResponseLiveData.offer(ApiResponse.create(Response.error<Foo>(400, body)))
        handleCreateCall = {
            apiResponseLiveData
        }

        dbData.offer(dbValue)

        networkBoundResource.asFlow().testSequence(
            {
                assert(it.status == Status.LOADING)
                assert(it.data == dbValue)
            }, {
                assert(it.status == Status.ERROR)
                assert(it.data == dbValue)
            }
        )
    }

    @Test
    fun dbSuccessWithReFetchSuccess() = runBlocking {
        val dbValue = Foo(1)
        val dbValue2 = Foo(2)
        val saved = AtomicReference<Foo>()
        handleShouldMatch = { foo -> foo === dbValue }
        handleSaveCallResult = { foo ->
            saved.set(foo)
            dbData.offer(dbValue2)
        }

        val networkResult = Foo(1)
        val apiResponseLiveData = TestFlow<ApiResponse<Foo>>()
        apiResponseLiveData.offer(ApiResponse.create(Response.success(networkResult)))
        handleCreateCall = { apiResponseLiveData }

        dbData.offer(dbValue)
        networkBoundResource.asFlow().testSequence(
            {
                assert(it.status == Status.LOADING)
                assert(it.data == dbValue)
            }, {
                assert(it.status == Status.SUCCESS)
                assert(it.data == dbValue2)
            }
        )
    }

    private data class Foo(var value: Int)
}
