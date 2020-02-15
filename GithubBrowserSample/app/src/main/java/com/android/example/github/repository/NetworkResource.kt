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

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.android.example.github.api.ApiEmptyResponse
import com.android.example.github.api.ApiErrorResponse
import com.android.example.github.api.ApiResponse
import com.android.example.github.api.ApiSuccessResponse
import com.android.example.github.vo.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest

/**
 * A generic class that can provide a resource straight from the network
 * It skips caching like NetworkBoundResource, in cases where there is no need to store
 * locally the data fetched from the network. The flow-object can still be able to capture
 * the data last emitted based flow.onComplete{}.
 *
 * You can read more about it in the [Architecture
 * Guide](https://developer.android.com/arch).
 * @param <RequestType>
</RequestType></ResultType> */
@FlowPreview
@ExperimentalCoroutinesApi
abstract class NetworkResource<RequestType> {
    suspend fun asFlow(): Flow<Resource<RequestType>> {
        return createCall().transformLatest { apiResponse ->
            when (apiResponse) {
                is ApiSuccessResponse -> {
                    emit(Resource.success(processResponse(apiResponse)))
                }

                is ApiEmptyResponse -> {
                    emit(Resource.success(null))
                }

                is ApiErrorResponse -> {
                    onFetchFailed()
                    emit(Resource.error(apiResponse.errorMessage, null))
                }
            }
        }.onStart {
            emit(Resource.loading(null))
        }
    }

    protected open fun onFetchFailed() {
        // Implement in sub-classes to handle errors
    }

    @WorkerThread
    protected open fun processResponse(response: ApiSuccessResponse<RequestType>) = response.body

    @MainThread
    protected abstract suspend fun createCall(): Flow<ApiResponse<RequestType>>
}
