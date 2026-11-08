package com.geowoof.feature.profile

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.geowoof.core.data.repository.ContentChanges
import com.geowoof.core.domain.ValidateDisplayNameUseCase
import com.geowoof.core.domain.ValidateProfileUseCase
import com.geowoof.core.model.AppError
import com.geowoof.core.model.DisplayNameIssue
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.ProfileCounts
import com.geowoof.core.model.RelationshipStatus
import com.geowoof.core.model.RemoteImage
import com.geowoof.core.model.RequestDirection
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.TextIssue
import com.geowoof.core.testing.FakeAuthRepository
import com.geowoof.core.testing.FakeMediaRepository
import com.geowoof.core.testing.FakePeopleRepository
import com.geowoof.core.testing.FakePetsRepository
import com.geowoof.core.testing.FakePostsRepository
import com.geowoof.core.testing.FakeProfileRepository
import com.geowoof.core.testing.FakeSocialRepository
import com.geowoof.core.testing.FakeWalksRepository
import com.geowoof.core.testing.MainDispatcherRule
import com.geowoof.core.testing.TestPets
import com.geowoof.core.testing.TestSocial
import com.geowoof.core.testing.TestUsers
import com.geowoof.core.testing.TestWalks
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProfileViewModelsTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val photo = RemoteImage("old-photo", "thumb", "medium")
    private val auth = FakeAuthRepository(SessionState.SignedIn(TestUsers.user()))
    private val profiles =
        FakeProfileRepository(TestPets.profile(displayName = "Ana", bio = "Hola", city = "Madrid", avatar = photo))
    private val pets = FakePetsRepository(listOf(TestPets.pet()))
    private val walks = FakeWalksRepository()
    private val people = FakePeopleRepository()
    private val social = FakeSocialRepository()
    private val posts = FakePostsRepository()
    private val media = FakeMediaRepository()

    private fun profile() = ProfileViewModel(auth, profiles, pets, walks, people, social, posts, ContentChanges())

    private fun editor() = EditProfileViewModel(profiles, media, ValidateProfileUseCase(ValidateDisplayNameUseCase()))

    @Test
    fun the_profile_screen_refreshes_and_shows_what_room_has() = runTest {
        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        val state = viewModel.uiState.value
        assertEquals("Ana", state.profile?.displayName)
        assertEquals(listOf("Luna"), state.pets.map { it.name })
        assertEquals(RelationshipStatus.Self, state.relationship?.status)
        assertTrue(state.isOwn)
        assertTrue(state.canOpenLists)
        assertEquals(1, profiles.refreshes)
        assertEquals(listOf("refreshMyPets"), pets.calls)
        assertEquals(listOf("refreshRequests"), social.calls)
        assertFalse(state.isLoading)
    }

    @Test
    fun the_numbers_and_the_totals_reach_the_screen() = runTest {
        people.countsResult = Outcome.Success(ProfileCounts(followers = 3, following = 2, friends = 1))
        walks.setTotals(null, TestWalks.totals())

        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(ProfileCounts(followers = 3, following = 2, friends = 1), viewModel.uiState.value.counts)
        assertEquals(TestWalks.totals(), viewModel.uiState.value.totals)
    }

    @Test
    fun the_requests_of_each_direction_are_counted() = runTest {
        social.receivedRequests.value = listOf(TestSocial.request("1"), TestSocial.request("2"))
        social.sentRequests.value = listOf(TestSocial.request("3", direction = RequestDirection.Sent))

        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(2, viewModel.uiState.value.receivedRequests)
        assertEquals(1, viewModel.uiState.value.sentRequests)
    }

    @Test
    fun a_failed_refresh_keeps_the_saved_data_and_explains_the_problem() = runTest {
        pets.refreshResult = Outcome.Failure(AppError.Network)

        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(AppError.Network, viewModel.uiState.value.loadError)
        assertEquals("Luna", viewModel.uiState.value.pets.single().name)

        viewModel.onErrorShown()
        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun coming_back_refreshes_without_showing_an_indicator_or_an_error() = runTest {
        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        pets.refreshResult = Outcome.Failure(AppError.Network)
        people.countsResult = Outcome.Success(ProfileCounts(followers = 99, following = 2, friends = 1))

        viewModel.onResume()

        assertEquals(99, viewModel.uiState.value.counts?.followers)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.loadError)
    }

    /** The screen opens and comes to the front right away: that second load must not hide the first failure. */
    @Test
    fun without_a_saved_profile_or_a_connection_the_screen_offers_to_retry_instead_of_waiting_forever() = runTest {
        val empty = FakeProfileRepository().apply { refreshResult = Outcome.Failure(AppError.Network) }
        val viewModel = ProfileViewModel(auth, empty, pets, walks, people, social, posts, ContentChanges())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(AppError.Network, viewModel.uiState.value.loadError)

        viewModel.onResume()

        assertNull(viewModel.uiState.value.profile)
        assertEquals(AppError.Network, viewModel.uiState.value.loadError)
    }

    @Test
    fun retrying_clears_the_failure_while_it_loads_and_when_it_works() = runTest {
        val empty = FakeProfileRepository().apply { refreshResult = Outcome.Failure(AppError.Network) }
        val viewModel = ProfileViewModel(auth, empty, pets, walks, people, social, posts, ContentChanges())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        empty.refreshResult = Outcome.Success(Unit)
        viewModel.refresh()

        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun with_a_saved_profile_a_failure_when_coming_back_says_nothing() = runTest {
        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        profiles.refreshResult = Outcome.Failure(AppError.Network)

        viewModel.onResume()

        assertEquals("Ana", viewModel.uiState.value.profile?.displayName)
        assertNull(viewModel.uiState.value.loadError)
    }

    @Test
    fun your_posts_and_what_you_saved_are_asked_for() = runTest {
        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.posts.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.saved.collect {} }

        assertEquals(listOf("user:${TestUsers.user().id}", "saved"), posts.reads)
    }

    @Test
    fun the_numbers_that_could_not_be_refreshed_stay_as_they_were() = runTest {
        val viewModel = profile()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        people.countsResult = Outcome.Failure(AppError.Network)

        viewModel.refresh()

        assertEquals(12, viewModel.uiState.value.counts?.followers)
        assertEquals(AppError.Network, viewModel.uiState.value.loadError)
    }

    @Test
    fun the_editor_starts_with_the_current_values() {
        val viewModel = editor()

        assertEquals("Ana", viewModel.displayName.text.toString())
        assertEquals("Hola", viewModel.bio.text.toString())
        assertEquals("Madrid", viewModel.city.text.toString())
        assertEquals(photo, viewModel.uiState.value.avatar)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun saving_without_a_new_photo_keeps_the_current_one() {
        val viewModel = editor()
        viewModel.displayName.setTextAndPlaceCursorAtEnd("Ana López")

        viewModel.save()

        assertEquals("old-photo", profiles.saved.single().avatarAssetId)
        assertEquals("Ana López", profiles.saved.single().displayName)
        assertTrue(media.uploads.isEmpty())
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun a_new_photo_is_uploaded_before_saving() {
        val viewModel = editor()
        media.result = Outcome.Success("new-photo")

        viewModel.onImagePicked("content://photo")
        viewModel.save()

        assertEquals(listOf("content://photo" to MediaPurpose.UserAvatar), media.uploads)
        assertEquals("new-photo", profiles.saved.single().avatarAssetId)
    }

    @Test
    fun the_photo_can_be_removed() {
        val viewModel = editor()

        viewModel.removePhoto()
        viewModel.save()

        assertFalse(viewModel.uiState.value.hasPhoto)
        assertNull(profiles.saved.single().avatarAssetId)
    }

    @Test
    fun a_photo_that_cannot_be_uploaded_stops_the_saving() {
        val viewModel = editor()
        val error = AppError.Api(status = 422, code = "media.invalid_image")
        media.result = Outcome.Failure(error)

        viewModel.onImagePicked("content://not-a-photo")
        viewModel.save()

        assertEquals(error, viewModel.uiState.value.error)
        assertTrue(profiles.saved.isEmpty())
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun invalid_fields_are_reported_without_saving() {
        val viewModel = editor()
        viewModel.displayName.setTextAndPlaceCursorAtEnd(" ")
        viewModel.bio.setTextAndPlaceCursorAtEnd("x".repeat(301))

        viewModel.save()

        assertEquals(DisplayNameIssue.Empty, viewModel.uiState.value.issues.displayName)
        assertEquals(TextIssue.TooLong, viewModel.uiState.value.issues.bio)
        assertTrue(profiles.saved.isEmpty())
    }

    @Test
    fun without_a_saved_profile_a_failed_download_is_explained() {
        val empty = FakeProfileRepository().apply { refreshResult = Outcome.Failure(AppError.Network) }

        val viewModel = EditProfileViewModel(empty, media, ValidateProfileUseCase(ValidateDisplayNameUseCase()))

        assertEquals(AppError.Network, viewModel.uiState.value.error)
    }
}
