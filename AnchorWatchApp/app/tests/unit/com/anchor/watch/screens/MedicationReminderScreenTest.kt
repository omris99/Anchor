package com.anchor.watch.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.anchor.watch.data.local.MedicationEntity
import com.anchor.watch.data.local.MedicationStatus
import com.anchor.watch.data.local.MedicationStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [33],
    qualifiers = "he-rIL-ldrtl",
    // Same AGP9/Robolectric ShadowPackageParser avoidance as MainWatchScreenTest:
    // host the Composable directly, no merged-manifest parsing.
    manifest = Config.NONE,
    application = android.app.Application::class,
)
class MedicationReminderScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private class FakeStore(private val seed: MedicationEntity) : MedicationStore {
        override suspend fun upsert(entity: MedicationEntity) = Unit
        override suspend fun upsertAll(entities: List<MedicationEntity>) = Unit
        override suspend fun byId(id: String): MedicationEntity? =
            if (id == seed.id) seed else null
        override suspend fun all(): List<MedicationEntity> = listOf(seed)
        override suspend fun unsynced(): List<MedicationEntity> = emptyList()
        override suspend fun markSynced(id: String) = Unit
    }

    private fun store() = FakeStore(
        MedicationEntity(
            id = "med-1",
            name = "אספירין",
            scheduledTime = "08:00",
            status = MedicationStatus.PENDING,
            userId = "self",
        ),
    )

    @Test
    fun tappingTaken_invokesOnConfirm_andRendersConfirmationState() {
        var confirmed = false
        var finished = false
        rule.setContent {
            MedicationReminderScreen(
                store = store(),
                medicationId = "med-1",
                onConfirm = { confirmed = true },
                onFinished = { finished = true },
                // Keep the acknowledgment up so we can assert it's shown.
                confirmationDisplayMs = 10_000L,
            )
        }

        rule.onNodeWithContentDescription("אישור נטילת תרופה")
            .assertIsDisplayed()
            .performClick()
        rule.waitForIdle()

        assertTrue("onConfirm (remote /confirm) must fire on tap", confirmed)
        // The confirmation acknowledgment replaces the button.
        rule.onNodeWithContentDescription("התרופה אושרה כנלקחה").assertIsDisplayed()
        // Within the display window the screen has not yet closed.
        assertTrue("screen should not finish during the acknowledgment window", !finished)
    }

    @Test
    fun missingMedication_finishesImmediately() {
        var finished = false
        val emptyStore = object : MedicationStore {
            override suspend fun upsert(entity: MedicationEntity) = Unit
            override suspend fun upsertAll(entities: List<MedicationEntity>) = Unit
            override suspend fun byId(id: String): MedicationEntity? = null
            override suspend fun all(): List<MedicationEntity> = emptyList()
            override suspend fun unsynced(): List<MedicationEntity> = emptyList()
            override suspend fun markSynced(id: String) = Unit
        }
        rule.setContent {
            MedicationReminderScreen(
                store = emptyStore,
                medicationId = "missing",
                onConfirm = {},
                onFinished = { finished = true },
            )
        }
        rule.waitForIdle()
        assertTrue(finished)
    }
}
