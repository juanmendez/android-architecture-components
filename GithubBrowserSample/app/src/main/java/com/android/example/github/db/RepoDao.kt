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

package com.android.example.github.db

import android.util.SparseIntArray
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.android.example.github.testing.OpenForTesting
import com.android.example.github.vo.Contributor
import com.android.example.github.vo.Repo
import com.android.example.github.vo.RepoSearchResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import java.util.Collections

/**
 * Interface for database access on Repo related operations.
 */
@Dao
@OpenForTesting
@ExperimentalCoroutinesApi
abstract class RepoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(vararg repos: Repo)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertContributors(contributors: List<Contributor>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insertRepos(repositories: List<Repo>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun createRepoIfNotExists(repo: Repo): Long

    @Query("SELECT * FROM repo WHERE owner_login = :ownerLogin AND name = :name")
    abstract fun load(ownerLogin: String, name: String): Flow<Repo?>

    @Query(
        """
        SELECT login, avatarUrl, repoName, repoOwner, contributions FROM contributor
        WHERE repoName = :name AND repoOwner = :owner
        ORDER BY contributions DESC"""
    )
    abstract fun loadContributors(owner: String, name: String): Flow<List<Contributor>?>

    @Query(
        """
        SELECT * FROM Repo
        WHERE owner_login = :owner
        ORDER BY stars DESC"""
    )
    abstract fun loadRepositories(owner: String): Flow<List<Repo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(result: RepoSearchResult)

    @Query("SELECT * FROM RepoSearchResult WHERE `query` = :query")
    abstract fun search(query: String): Flow<RepoSearchResult?>

    suspend fun loadOrdered(repoIds: List<Int>): List<Repo> {
        val order = SparseIntArray()
        repoIds.withIndex().forEach {
            order.put(it.value, it.index)
        }

        return loadById(repoIds).apply {
            Collections.sort(this) { r1, r2 ->
                val pos1 = order.get(r1.id)
                val pos2 = order.get(r2.id)
                pos1 - pos2
            }
        }
    }

    @Query("SELECT * FROM Repo WHERE id in (:repoIds)")
    protected abstract suspend fun loadById(repoIds: List<Int>): List<Repo>

    @Query("SELECT * FROM RepoSearchResult WHERE `query` = :query")
    abstract fun findSearchResult(query: String): RepoSearchResult?
}
