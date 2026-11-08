package com.geowoof.feature.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.geowoof.core.designsystem.icon.GeoWoofIcons
import com.geowoof.core.designsystem.theme.GeoWoofTheme
import com.geowoof.core.model.Pet
import com.geowoof.core.ui.ImageVariant
import com.geowoof.core.ui.PetFacts
import com.geowoof.core.ui.RemoteAvatar
import com.geowoof.core.ui.shortDescription

/**
 * The pets of a profile, over its tabs. Yours open their page; those of someone else, which have no page, open over
 * the profile with [onShowPet]. In your own the row is always there, with the circle to add one at the end, and in
 * that of someone else only if they have any.
 */
internal fun LazyListScope.petHighlights(pets: List<Pet>, actions: ProfileActions, onShowPet: (petId: String) -> Unit) {
    val onAddPet = actions.onAddPet
    if (pets.isEmpty() && onAddPet == null) return
    val onOpenPet = actions.onOpenPet ?: onShowPet
    item(contentType = HIGHLIGHTS_TYPE) {
        PetHighlights(pets, onOpen = { pet -> onOpenPet(pet.id) }, onAdd = onAddPet)
    }
}

/**
 * The pets as circles with their name in a row, like the highlights of a profile in other apps. The last circle adds
 * a pet when there is [onAdd], and it is the only one while there are none.
 */
@Composable
internal fun PetHighlights(
    pets: List<Pet>,
    onOpen: (Pet) -> Unit,
    onAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val spacing = GeoWoofTheme.spacing
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        items(pets, key = Pet::id) { pet ->
            Highlight(label = pet.name, onClick = { onOpen(pet) }) {
                RemoteAvatar(
                    image = pet.avatar,
                    fallbackIcon = GeoWoofIcons.Pets,
                    contentDescription = null,
                    size = HighlightPhotoSize,
                )
            }
        }
        if (onAdd != null) {
            item(key = ADD_KEY) { AddPetHighlight(onAdd) }
        }
    }
}

/** It says what it does to TalkBack, and only "New" on the screen, where the plus already says it. */
@Composable
private fun AddPetHighlight(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Highlight(
        label = stringResource(R.string.feature_profile_new_pet),
        onClick = onClick,
        modifier = modifier,
        labelModifier = Modifier.clearAndSetSemantics {},
    ) {
        Surface(
            modifier = Modifier.size(HighlightPhotoSize),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(GeoWoofIcons.Add),
                    contentDescription = stringResource(R.string.feature_profile_add_pet),
                )
            }
        }
    }
}

/** A circle with a thin ring and its name under it. The whole of it is what is touched. */
@Composable
private fun Highlight(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelModifier: Modifier = Modifier,
    circle: @Composable () -> Unit,
) {
    val spacing = GeoWoofTheme.spacing
    Column(
        modifier = modifier
            .width(HighlightWidth)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Box(
            modifier = Modifier
                .size(HighlightRingSize)
                .border(RingWidth, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            circle()
        }
        Text(
            text = label,
            modifier = labelModifier,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The pet of someone else: it has no page of its own, which is for its owner, so what the tab showed of it and the
 * rest of what the owner tells about it opens here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PetSheet(pet: Pet, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = GeoWoofTheme.spacing
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = spacing.extraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            RemoteAvatar(
                image = pet.avatar,
                fallbackIcon = GeoWoofIcons.Pets,
                contentDescription = null,
                size = SheetPhotoSize,
                variant = ImageVariant.Medium,
            )
            Text(
                text = pet.name,
                modifier = Modifier
                    .padding(top = spacing.small)
                    .semantics { heading() },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = pet.shortDescription(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pet.bio?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = spacing.large),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            PetFacts(
                pet = pet,
                modifier = Modifier.padding(top = spacing.medium),
                tileColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

private const val ADD_KEY = "add"
private const val HIGHLIGHTS_TYPE = "pets"
private val HighlightPhotoSize = 60.dp
private val RingWidth = 1.dp
private val HighlightRingSize = 68.dp
private val HighlightWidth = 76.dp
private val SheetPhotoSize = 96.dp
