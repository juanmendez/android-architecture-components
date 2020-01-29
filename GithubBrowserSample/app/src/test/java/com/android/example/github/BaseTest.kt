package com.android.example.github

import com.android.example.github.util.TestCoroutineRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.mockito.MockitoAnnotations

@InternalCoroutinesApi
@ExperimentalCoroutinesApi
@FlowPreview
abstract class BaseTest {
    @get:Rule
    val testCoroutineRule = TestCoroutineRule()

    @Before
    fun onBefore() {
        MockitoAnnotations.initMocks(this)
    }
}