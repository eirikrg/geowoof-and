package com.geowoof.feature.profile.people

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.geowoof.core.data.repository.RemoteLoadException
import com.geowoof.core.designsystem.component.EmptyState
import com.geowoof.core.designsystem.component.ErrorState
import com.geowoof.core.designsystem.component.GeoWoofIllustrations
import com.geowoof.core.designsystem.component.SkeletonList
import com.geowoof.core.designsystem.component.SkeletonRow
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.PeopleListKind
import com.geowoof.core.model.Person
import com.geowoof.core.model.UserSummary
import com.geowoof.core.ui.PagedContent
import com.geowoof.core.ui.RefreshOnReturn
import com.geowoof.core.ui.UserRow
import com.geowoof.core.ui.pagedContent
import com.geowoof.core.ui.userMessage
import com.geowoof.feature.profile.R
import kotlinx.coroutines.flow.flowOf
import java.time.Instant

@Composable
internal fun PeopleListScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PeopleListViewModel = hiltViewModel(),
) {
    val people = viewModel.people.collectAsLazyPagingItems()
    // Back from a profile, the person may have followed, unfollowed or blocked someone in it.
    RefreshOnReturn(people, viewModel.changes)
    PeopleListContent(
        kind = viewModel.kind,
        people = people,
        onBack = onBack,
        onOpenUser = onOpenUser,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeopleListContent(
    kind: PeopleListKind,
    people: LazyPagingItems<Person>,
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(kind.titleRes())) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(GeoWoofIcons.ArrowBack),
                            contentDescription = stringResource(R.string.feature_profile_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        val failure = (people.loadState.refresh as? LoadState.Error)?.error as? RemoteLoadException
        when (people.pagedContent()) {
            PagedContent.Loading -> SkeletonList(modifier = contentModifier) { SkeletonRow() }

            PagedContent.Error -> ErrorState(
                title = stringResource(R.string.feature_profile_people_error_title),
                message = failure?.error?.userMessage().orEmpty(),
                retryLabel = stringResource(R.string.feature_profile_retry),
                onRetry = people::retry,
                modifier = contentModifier,
            )

            PagedContent.Empty -> EmptyState(
                icon = GeoWoofIcons.Groups,
                title = stringResource(kind.emptyTitleRes()),
                message = stringResource(kind.emptyMessageRes()),
                illustration = GeoWoofIllustrations.EmptyFriends,
                modifier = contentModifier,
            )

            PagedContent.Items -> LazyColumn(modifier = contentModifier) {
                items(people.itemCount, key = people.itemKey { it.user.id }) { index ->
                    people[index]?.let { person ->
                        UserRow(person.user, onClick = { onOpenUser(person.user.id) })
                    }
                }
            }
        }
    }
}

@StringRes
private fun PeopleListKind.titleRes(): Int = when (this) {
    PeopleListKind.Followers -> R.string.feature_profile_followers
    PeopleListKind.Following -> R.string.feature_profile_following
    PeopleListKind.Friends -> R.string.feature_profile_friends
}

@StringRes
private fun PeopleListKind.emptyTitleRes(): Int = when (this) {
    PeopleListKind.Followers -> R.string.feature_profile_no_followers_title
    PeopleListKind.Following -> R.string.feature_profile_no_following_title
    PeopleListKind.Friends -> R.string.feature_profile_no_friends_title
}

@StringRes
private fun PeopleListKind.emptyMessageRes(): Int = when (this) {
    PeopleListKind.Followers -> R.string.feature_profile_no_followers_message
    PeopleListKind.Following -> R.string.feature_profile_no_following_message
    PeopleListKind.Friends -> R.string.feature_profile_no_friends_message
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun PeopleListPreview() {
    val finished = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
    val person = Person(UserSummary("1", "bea", "Bea", "Madrid", null), Instant.parse("2026-11-05T10:00:00Z"))
    GeoWoofTheme(dynamicColor = false) {
        PeopleListContent(
            kind = PeopleListKind.Followers,
            people = flowOf(PagingData.from(listOf(person), finished)).collectAsLazyPagingItems(),
            onBack = {},
            onOpenUser = {},
        )
    }
}
