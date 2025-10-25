package com.example.momentag.viewmodel

import com.example.momentag.repository.RemoteRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class PhotoTagViewModelTest {
    @get:Rule
    val mainCoroutineRule = MainCoroutineRule()

    private lateinit var remoteRepository: RemoteRepository

    private lateinit var viewModel: PhotoTagViewModel

    @Before
    fun setUp() {
        remoteRepository = mock()
        viewModel = PhotoTagViewModel(remoteRepository)
    }

    @Test
    fun setInitialDataTest() {
        val tagName = "Test Tag"
        val photos = listOf(1L, 2L)

        viewModel.setInitialData(tagName, photos)

        assertEquals(tagName, viewModel.tagName.value)
        assertEquals(photos, viewModel.selectedPhotos.value)
    }

    @Test
    fun setInitialDataNULLTest() {
        val photos = listOf(1L, 2L)

        viewModel.setInitialData(null, photos)

        assertEquals("", viewModel.tagName.value)
        assertEquals(photos, viewModel.selectedPhotos.value)
    }

    @Test
    fun updateTagNameTest() {
        val newTagName = "New Name"

        viewModel.updateTagName(newTagName)

        assertEquals(newTagName, viewModel.tagName.value)
    }

    @Test
    fun addPhotoTest() {
        viewModel.setInitialData("Tag", listOf(1L))

        viewModel.addPhoto(2L)

        assertEquals(listOf(1L, 2L), viewModel.selectedPhotos.value)
    }

    @Test
    fun removePhotoTest() {
        viewModel.setInitialData("Tag", listOf(1L, 2L, 3L))

        viewModel.removePhoto(2L)

        assertEquals(listOf(1L, 3L), viewModel.selectedPhotos.value)
    }

    @Test
    fun resetSaveStateTest() {
        viewModel.saveTagAndPhotos()
        assertTrue(viewModel.saveState.value is PhotoTagViewModel.SaveState.Error)

        viewModel.resetSaveState()

        assertTrue(viewModel.saveState.value is PhotoTagViewModel.SaveState.Idle)
    }

    @Test
    fun saveTagAndPhotosWithNULLTagTest() =
        runTest {
            viewModel.setInitialData(null, listOf(1L, 2L))

            viewModel.saveTagAndPhotos()

            val state = viewModel.saveState.value
            assertTrue(state is PhotoTagViewModel.SaveState.Error)
            assertEquals("Tag cannot be empty and photos must be selected", (state as PhotoTagViewModel.SaveState.Error).message)
        }

    @Test
    fun saveTagAndPhotosNULLPhotoTest() =
        runTest {
            viewModel.updateTagName("Test Tag")

            viewModel.saveTagAndPhotos()

            val state = viewModel.saveState.value
            assertTrue(state is PhotoTagViewModel.SaveState.Error)
            assertEquals("Tag cannot be empty and photos must be selected", (state as PhotoTagViewModel.SaveState.Error).message)
        }

    @Test
    fun saveTagAndPhotosAddTagErrorTest() =
        runTest {
            val tagName = "Fail Tag"
            viewModel.setInitialData(tagName, listOf(1L))
            val errorResult = RemoteRepository.Result.Error<Long>(500, "Server Error")
            whenever(remoteRepository.postTags(tagName)).thenReturn(errorResult)

            viewModel.saveTagAndPhotos()

            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.saveState.value
            assertTrue(state is PhotoTagViewModel.SaveState.Error)
            assertEquals("Error creating tag", (state as PhotoTagViewModel.SaveState.Error).message)
            verify(remoteRepository, never()).postTagsToPhoto(any(), any())
        }

    @Test
    fun saveTagAndPhotosPostTagsToPhotoErrorTest() =
        runTest {
            val tagName = "Partial Fail"
            val photos = listOf(1L, 2L)
            val newTagId = 123L
            viewModel.setInitialData(tagName, photos)

            val tagSuccessResult = RemoteRepository.Result.Success(newTagId)
            val photoSuccessResult = RemoteRepository.Result.Success(Unit)
            val photoFailResult = RemoteRepository.Result.BadRequest<Unit>("Bad request")

            whenever(remoteRepository.postTags(tagName)).thenReturn(tagSuccessResult)
            whenever(remoteRepository.postTagsToPhoto(1L, newTagId)).thenReturn(photoSuccessResult)
            whenever(remoteRepository.postTagsToPhoto(2L, newTagId)).thenReturn(photoFailResult)

            viewModel.saveTagAndPhotos()

            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            val state = viewModel.saveState.value
            assertTrue(state is PhotoTagViewModel.SaveState.Error)
            assertEquals("Bad Request: Bad request", (state as PhotoTagViewModel.SaveState.Error).message)

            verify(remoteRepository).postTags(tagName)
            verify(remoteRepository).postTagsToPhoto(1L, newTagId)
            verify(remoteRepository).postTagsToPhoto(2L, newTagId)
        }

    @Test
    fun saveTagAndPhotosTest() =
        runTest {
            val tagName = "Success Tag"
            val photos = listOf(1L, 2L)
            val newTagId = 100L
            viewModel.setInitialData(tagName, photos)

            whenever(remoteRepository.postTags(tagName))
                .thenReturn(RemoteRepository.Result.Success(newTagId))
            whenever(remoteRepository.postTagsToPhoto(1L, newTagId))
                .thenReturn(RemoteRepository.Result.Success(Unit))
            whenever(remoteRepository.postTagsToPhoto(2L, newTagId))
                .thenReturn(RemoteRepository.Result.Success(Unit))

            viewModel.saveTagAndPhotos()

            mainCoroutineRule.testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.saveState.value is PhotoTagViewModel.SaveState.Success)

            verify(remoteRepository).postTags(tagName)
            verify(remoteRepository, times(2)).postTagsToPhoto(any(), eq(newTagId))
            verify(remoteRepository).postTagsToPhoto(1L, newTagId)
            verify(remoteRepository).postTagsToPhoto(2L, newTagId)
        }
}
