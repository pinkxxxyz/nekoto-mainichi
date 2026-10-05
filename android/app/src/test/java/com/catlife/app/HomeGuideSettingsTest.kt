package com.catlife.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import com.catlife.app.settings.HomeGuideSettings
import com.catlife.app.settings.TutorialCompletedKey
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class HomeGuideSettingsTest {
    @Test fun newInstallStaysPendingAfterTutorialCompletionUntilGuideCompletes() = runBlocking {
        val dir = Files.createTempDirectory("home-guide-new").toFile()
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("onboarding.preferences_pb") })
            val guide = HomeGuideSettings(store)
            guide.initialize()
            assertEquals(false, guide.completed.first())
            store.edit { it[TutorialCompletedKey] = true }
            guide.initialize()
            assertEquals(false, guide.completed.first())
            guide.complete()
            assertEquals(true, guide.completed.first())
            assertEquals(true, store.data.first()[TutorialCompletedKey])
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun existingUserIsNotTreatedAsFirstLaunch() = runBlocking {
        val dir = Files.createTempDirectory("home-guide-legacy").toFile()
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("onboarding.preferences_pb") })
            store.edit { it[TutorialCompletedKey] = true }
            val guide = HomeGuideSettings(store)
            guide.initialize()
            assertEquals(true, guide.completed.first())
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun completedGuideDoesNotReappearAfterStoreRecreation() = runBlocking {
        val dir = Files.createTempDirectory("home-guide-restart").toFile()
        val file = dir.resolve("onboarding.preferences_pb")
        val firstJob = SupervisorJob()
        val firstStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob), produceFile = { file })
        val guide = HomeGuideSettings(firstStore)
        guide.initialize()
        guide.complete()
        firstJob.cancelAndJoin()
        val nextJob = SupervisorJob()
        try {
            val nextStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + nextJob), produceFile = { file })
            val nextGuide = HomeGuideSettings(nextStore)
            nextGuide.initialize()
            assertEquals(true, nextGuide.completed.first())
        } finally { nextJob.cancelAndJoin(); dir.deleteRecursively() }
    }
}
