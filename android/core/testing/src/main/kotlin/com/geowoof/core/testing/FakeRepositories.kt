package com.geowoof.core.testing

import com.geowoof.core.data.repository.AccountRepository
import com.geowoof.core.data.repository.AuthRepository
import com.geowoof.core.data.repository.MediaRepository
import com.geowoof.core.data.repository.PetsRepository
import com.geowoof.core.data.repository.ProfileRepository
import com.geowoof.core.model.AppError
import com.geowoof.core.model.AuthProviders
import com.geowoof.core.model.MediaPurpose
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.Pet
import com.geowoof.core.model.PetDraft
import com.geowoof.core.model.Profile
import com.geowoof.core.model.ProfileDraft
import com.geowoof.core.model.SessionState
import com.geowoof.core.model.User
import com.geowoof.core.model.UsernameAvailability
import com.geowoof.core.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * Configurable [AuthRepository]: each operation returns the outcome set by the test and records
 * its arguments. Successful sign-ins update [sessionState] like the real repository.
 */
class FakeAuthRepository(initialState: SessionState = SessionState.SignedOut) : AuthRepository {
    private val state = MutableStateFlow(initialState)
    override val sessionState: StateFlow<SessionState> = state

    var providers: Outcome<AuthProviders> = Outcome.Success(AuthProviders(google = false))
    var signInResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var registerResult: Outcome<User> = Outcome.Success(TestUsers.user(username = null, emailVerified = false))
    var googleResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var verifyResult: Outcome<User> = Outcome.Success(TestUsers.user(emailVerified = true))
    var unitResult: Outcome<Unit> = Outcome.Success(Unit)

    val calls = mutableListOf<String>()
    var refreshes = 0
        private set

    fun setSession(sessionState: SessionState) {
        state.value = sessionState
    }

    override suspend fun authProviders() = providers

    override suspend fun register(email: String, password: String, displayName: String) =
        record("register:$email:$displayName", registerResult)

    override suspend fun signIn(email: String, password: String) = record("signIn:$email", signInResult)

    override suspend fun signInWithGoogle(idToken: String) = record("google:$idToken", googleResult)

    override suspend fun signOut() {
        calls += "signOut"
        state.value = SessionState.SignedOut
    }

    override suspend fun resendVerificationEmail() = recordUnit("resend")

    override suspend fun verifyEmail(code: String) = record("verify:$code", verifyResult)

    override suspend fun requestPasswordReset(email: String) = recordUnit("forgot:$email")

    override suspend fun resetPassword(email: String, code: String, newPassword: String) =
        recordUnit("reset:$email:$code")

    override suspend fun changePassword(currentPassword: String?, newPassword: String) =
        recordUnit("changePassword:$currentPassword")

    override suspend fun deleteAccount(password: String?, googleIdToken: String?): Outcome<Unit> {
        val outcome = recordUnit("delete:$password:$googleIdToken")
        if (outcome is Outcome.Success) {
            state.value = SessionState.SignedOut
        }

        return outcome
    }

    override suspend fun refreshUser(): Outcome<User> {
        refreshes++
        return (state.value as? SessionState.SignedIn)?.let { Outcome.Success(it.user) }
            ?: Outcome.Success(TestUsers.user())
    }

    private fun record(call: String, outcome: Outcome<User>): Outcome<User> {
        calls += call
        if (outcome is Outcome.Success) {
            state.value = SessionState.SignedIn(outcome.value)
        }

        return outcome
    }

    private fun recordUnit(call: String): Outcome<Unit> {
        calls += call
        return unitResult
    }
}

class FakeAccountRepository : AccountRepository {
    var availability: Outcome<UsernameAvailability> = Outcome.Success(
        UsernameAvailability("ana", available = true, reason = null),
    )
    var chooseResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var updateResult: Outcome<User> = Outcome.Success(TestUsers.user())
    var availabilityChecks = 0
        private set
    val chosenUsernames = mutableListOf<String>()
    val displayNames = mutableListOf<String>()

    override suspend fun usernameAvailability(username: String): Outcome<UsernameAvailability> {
        availabilityChecks++
        return availability
    }

    override suspend fun chooseUsername(username: String): Outcome<User> {
        chosenUsernames += username
        return chooseResult
    }

    override suspend fun updateDisplayName(displayName: String): Outcome<User> {
        displayNames += displayName
        return updateResult
    }
}

class FakeProfileRepository(initial: Profile? = null) : ProfileRepository {
    private val profile = MutableStateFlow(initial)
    override val myProfile: Flow<Profile?> = profile

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var saveResult: Outcome<Profile>? = null
    var refreshes = 0
        private set
    val saved = mutableListOf<ProfileDraft>()

    fun setProfile(value: Profile?) {
        profile.value = value
    }

    override suspend fun refresh(): Outcome<Unit> {
        refreshes++
        return refreshResult
    }

    override suspend fun save(draft: ProfileDraft): Outcome<Profile> {
        saved += draft
        val result = saveResult ?: Outcome.Success(
            TestPets.profile(displayName = draft.displayName, bio = draft.bio, city = draft.city),
        )
        if (result is Outcome.Success) {
            profile.value = result.value
        }

        return result
    }
}

class FakePetsRepository(initial: List<Pet> = emptyList()) : PetsRepository {
    private val pets = MutableStateFlow(initial)
    private val weights = MutableStateFlow<Map<String, List<WeightEntry>>>(emptyMap())
    private var nextId = 1

    override val myPets: Flow<List<Pet>> = pets

    var refreshResult: Outcome<Unit> = Outcome.Success(Unit)
    var writeFailure: AppError? = null
    var weightFailure: AppError? = null
    val created = mutableListOf<PetDraft>()
    val updated = mutableListOf<Pair<String, PetDraft>>()
    val deleted = mutableListOf<String>()
    val calls = mutableListOf<String>()

    fun setWeights(petId: String, entries: List<WeightEntry>) {
        weights.update { it + (petId to entries) }
    }

    override fun observePet(petId: String): Flow<Pet?> = pets.map { list -> list.find { it.id == petId } }

    override suspend fun refreshMyPets(): Outcome<Unit> {
        calls += "refreshMyPets"
        return refreshResult
    }

    override suspend fun refreshPet(petId: String): Outcome<Unit> {
        calls += "refreshPet:$petId"
        return refreshResult
    }

    override suspend fun create(draft: PetDraft): Outcome<Pet> {
        writeFailure?.let { return Outcome.Failure(it) }
        created += draft
        val pet = TestPets.pet(id = "pet-${nextId++}", name = draft.name)
        pets.update { it + pet }
        return Outcome.Success(pet)
    }

    override suspend fun update(petId: String, draft: PetDraft): Outcome<Pet> {
        writeFailure?.let { return Outcome.Failure(it) }
        updated += petId to draft
        val pet = TestPets.pet(id = petId, name = draft.name)
        pets.update { list -> list.map { if (it.id == petId) pet else it } }
        return Outcome.Success(pet)
    }

    override suspend fun delete(petId: String): Outcome<Unit> {
        writeFailure?.let { return Outcome.Failure(it) }
        deleted += petId
        pets.update { list -> list.filterNot { it.id == petId } }
        return Outcome.Success(Unit)
    }

    override fun observeWeights(petId: String): Flow<List<WeightEntry>> = weights.map { it[petId].orEmpty() }

    override suspend fun refreshWeights(petId: String): Outcome<Unit> {
        calls += "refreshWeights:$petId"
        return refreshResult
    }

    override suspend fun addWeight(petId: String, measuredOn: LocalDate, weightKg: Double): Outcome<WeightEntry> {
        weightFailure?.let { return Outcome.Failure(it) }
        val entry = WeightEntry("w-${nextId++}", petId, measuredOn, weightKg)
        weights.update { it + (petId to (it[petId].orEmpty() + entry).sortedBy(WeightEntry::measuredOn)) }
        return Outcome.Success(entry)
    }

    override suspend fun updateWeight(
        petId: String,
        entryId: String,
        measuredOn: LocalDate,
        weightKg: Double,
    ): Outcome<WeightEntry> {
        weightFailure?.let { return Outcome.Failure(it) }
        val entry = WeightEntry(entryId, petId, measuredOn, weightKg)
        weights.update { current ->
            current +
                (
                    petId to
                        current[petId].orEmpty().map {
                            if (it.id ==
                                entryId
                            ) {
                                entry
                            } else {
                                it
                            }
                        }.sortedBy(WeightEntry::measuredOn)
                    )
        }
        return Outcome.Success(entry)
    }

    override suspend fun deleteWeight(petId: String, entryId: String): Outcome<Unit> {
        weightFailure?.let { return Outcome.Failure(it) }
        weights.update { current -> current + (petId to current[petId].orEmpty().filterNot { it.id == entryId }) }
        return Outcome.Success(Unit)
    }
}

class FakeMediaRepository : MediaRepository {
    var result: Outcome<String> = Outcome.Success("asset-1")
    val uploads = mutableListOf<Pair<String, MediaPurpose>>()

    override suspend fun uploadImage(contentUri: String, purpose: MediaPurpose): Outcome<String> {
        uploads += contentUri to purpose
        return result
    }

    override suspend fun uploadStoredImage(path: String, purpose: MediaPurpose): Outcome<String> =
        uploadImage(path, purpose)
}
