package com.example.momentag.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ViewModelFactoryTest {
    private lateinit var context: Context
    private lateinit var factory: ViewModelFactory

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        factory = ViewModelFactory.getInstance(context)
    }

    // ========== Singleton Tests ==========

    @Test
    fun `getInstance returns same instance`() {
        // Given
        val factory1 = ViewModelFactory.getInstance(context)

        // When
        val factory2 = ViewModelFactory.getInstance(context)

        // Then
        assertSame(factory1, factory2)
    }

    @Test
    fun `getInstance with different context returns same instance`() {
        // Given
        val factory1 = ViewModelFactory.getInstance(context)
        val newContext = ApplicationProvider.getApplicationContext<Context>()

        // When
        val factory2 = ViewModelFactory.getInstance(newContext)

        // Then
        assertSame(factory1, factory2)
    }

    // ========== ViewModel Creation Tests ==========

    @Test
    fun `create HomeViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(HomeViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is HomeViewModel)
    }

    @Test
    fun `create LocalViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(LocalViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is LocalViewModel)
    }

    @Test
    fun `create AuthViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(AuthViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is AuthViewModel)
    }

    @Test
    fun `create SearchViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(SearchViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is SearchViewModel)
    }

    @Test
    fun `create ImageDetailViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(ImageDetailViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is ImageDetailViewModel)
    }

    @Test
    fun `create PhotoViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(PhotoViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is PhotoViewModel)
    }

    @Test
    fun `create AddTagViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(AddTagViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is AddTagViewModel)
    }

    @Test
    fun `create SelectImageViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(SelectImageViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is SelectImageViewModel)
    }

    @Test
    fun `create AlbumViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(AlbumViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is AlbumViewModel)
    }

    @Test
    fun `create StoryViewModel returns correct instance`() {
        // When
        val viewModel = factory.create(StoryViewModel::class.java)

        // Then
        assertNotNull(viewModel)
        assertTrue(viewModel is StoryViewModel)
    }

    @Test
    fun `create same ViewModel multiple times returns new instances`() {
        // Given
        val viewModel1 = factory.create(AuthViewModel::class.java)

        // When
        val viewModel2 = factory.create(AuthViewModel::class.java)

        // Then
        assertNotSame(viewModel1, viewModel2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `create unknown ViewModel throws exception`() {
        // When - trying to create unknown ViewModel
        factory.create(UnknownViewModel::class.java)
    }

    // ========== Integration Tests ==========

    @Test
    fun `factory can create all ViewModels sequentially`() {
        // When - create all ViewModels
        val homeViewModel = factory.create(HomeViewModel::class.java)
        val localViewModel = factory.create(LocalViewModel::class.java)
        val authViewModel = factory.create(AuthViewModel::class.java)
        val searchViewModel = factory.create(SearchViewModel::class.java)
        val imageDetailViewModel = factory.create(ImageDetailViewModel::class.java)
        val photoViewModel = factory.create(PhotoViewModel::class.java)
        val addTagViewModel = factory.create(AddTagViewModel::class.java)
        val selectImageViewModel = factory.create(SelectImageViewModel::class.java)
        val albumViewModel = factory.create(AlbumViewModel::class.java)
        val storyViewModel = factory.create(StoryViewModel::class.java)

        // Then - all are created successfully
        assertNotNull(homeViewModel)
        assertNotNull(localViewModel)
        assertNotNull(authViewModel)
        assertNotNull(searchViewModel)
        assertNotNull(imageDetailViewModel)
        assertNotNull(photoViewModel)
        assertNotNull(addTagViewModel)
        assertNotNull(selectImageViewModel)
        assertNotNull(albumViewModel)
        assertNotNull(storyViewModel)
    }

    // Dummy ViewModel for testing unknown ViewModel
    private class UnknownViewModel : androidx.lifecycle.ViewModel()
}
