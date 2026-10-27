package com.example.youtubegesturecontrol.wakeword

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordModelsTest {
    @Test
    fun bundledModelIsConfiguredWithOneEnglishPhrase() {
        val status = WakeWordModels.status(emptyList())
        assertTrue(status.configured)
        assertTrue(status.missingAssets.isEmpty())
    }

    @Test
    fun missingModelFileIsNotConfigured() {
        val status = WakeWordModels.status(listOf(WakeWordModels.ENCODER))
        assertFalse(status.configured)
        assertEquals(listOf(WakeWordModels.ENCODER), status.missingAssets)
    }
}
