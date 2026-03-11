package com.phantom.ghostshift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhotoRepositoryTest {
    @Test
    fun buildExportFileName_keepsTagAndUsesTimestampSuffix() {
        val actual = PhotoRepository.buildExportFileName("IN-1", 0L)

        assertEquals("IN-1_19700101_000000_000.jpg", actual)
    }

    @Test
    fun buildExportFileName_changesWhenTimestampChanges() {
        val first = PhotoRepository.buildExportFileName("OUT-2", 1_000L)
        val second = PhotoRepository.buildExportFileName("OUT-2", 2_000L)

        assertNotEquals(first, second)
    }
}
