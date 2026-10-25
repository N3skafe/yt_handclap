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
        assertEquals(WakeWordModels.PHRASE, status.phraseLabel)
        assertTrue(status.detail.contains("SENA"))
    }

    @Test
    fun missingModelFileIsNotConfigured() {
        val status = WakeWordModels.status(listOf(WakeWordModels.ENCODER))
        assertFalse(status.configured)
        assertEquals("설정되지 않음", status.phraseLabel)
        assertTrue(status.detail.contains(WakeWordModels.ENCODER))
    }
}
