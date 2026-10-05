package com.catlife.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.catlife.app.settings.TutorialSettings
import com.catlife.app.settings.shouldShowTutorial
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class TutorialSettingsTest {
    private fun withSettings(test: suspend (TutorialSettings) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("tutorial-test").toFile()
        val job = SupervisorJob()
        try {
            val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { dir.resolve("tutorial.preferences_pb") })
            test(TutorialSettings(store))
        } finally { job.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun initialStateShowsTutorial() = withSettings { settings ->
        assertFalse(settings.completed.first())
        assertTrue(shouldShowTutorial(settings.completed.first(), manuallyRequested = false))
    }

    @Test fun merelyReadingOrLeavingIntermediatePageDoesNotComplete() = withSettings { settings ->
        assertTrue(shouldShowTutorial(settings.completed.first(), manuallyRequested = false))
        for (page in 0..3) {
            try { settings.complete(page); fail("intermediate page completed") } catch (_: IllegalArgumentException) { }
        }
        assertFalse(settings.completed.first())
    }

    @Test fun completionPersistsAndNextLaunchSkipsTutorial() = runBlocking {
        val dir = Files.createTempDirectory("tutorial-restart").toFile()
        val file = dir.resolve("tutorial.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        TutorialSettings(store).complete(4)
        job.cancelAndJoin()
        val nextJob = SupervisorJob()
        try {
            val nextStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + nextJob), produceFile = { file })
            val settings = TutorialSettings(nextStore)
            assertTrue(settings.completed.first())
            assertFalse(shouldShowTutorial(settings.completed.first(), manuallyRequested = false))
        } finally { nextJob.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun interruptedFirstLaunchShowsTutorialAgainAfterRecreation() = runBlocking {
        val dir = Files.createTempDirectory("tutorial-interrupted").toFile()
        val file = dir.resolve("tutorial.preferences_pb")
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job), produceFile = { file })
        assertFalse(TutorialSettings(store).completed.first())
        job.cancelAndJoin()
        val nextJob = SupervisorJob()
        try {
            val nextStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + nextJob), produceFile = { file })
            assertTrue(shouldShowTutorial(TutorialSettings(nextStore).completed.first(), manuallyRequested = false))
        } finally { nextJob.cancelAndJoin(); dir.deleteRecursively() }
    }

    @Test fun manualReplayDoesNotResetCompletionAndRemainsCompletedAfterFinishing() = withSettings { settings ->
        settings.complete(4)
        assertTrue(shouldShowTutorial(settings.completed.first(), manuallyRequested = true))
        assertTrue(settings.completed.first())
        settings.complete(4)
        assertTrue(settings.completed.first())
        assertFalse(shouldShowTutorial(settings.completed.first(), manuallyRequested = false))
    }
}
