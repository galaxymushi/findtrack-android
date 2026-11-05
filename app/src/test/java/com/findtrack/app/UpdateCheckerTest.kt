package com.findtrack.app

import com.findtrack.app.update.UpdateChecker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer patch is an update`() {
        assertTrue(UpdateChecker.isNewer("1.2.1", "1.2.0"))
    }

    @Test
    fun `newer minor and major are updates`() {
        assertTrue(UpdateChecker.isNewer("1.3.0", "1.2.9"))
        assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    }

    @Test
    fun `same version is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.3.0", "1.3.0"))
    }

    @Test
    fun `older version is not an update`() {
        assertFalse(UpdateChecker.isNewer("1.2.0", "1.3.0"))
    }

    @Test
    fun `tags and debug suffixes are ignored`() {
        assertTrue(UpdateChecker.isNewer("v1.4.0", "1.3.0-debug"))
        assertFalse(UpdateChecker.isNewer("v1.3.0", "1.3.0-debug"))
    }

    @Test
    fun `missing trailing parts count as zero`() {
        assertTrue(UpdateChecker.isNewer("1.3.1", "1.3"))
        assertFalse(UpdateChecker.isNewer("1.3", "1.3.0"))
    }

    @Test
    fun `non numeric versions never trigger an update`() {
        assertFalse(UpdateChecker.isNewer("latest", "1.3.0"))
        assertFalse(UpdateChecker.isNewer("1.3.0", "unknown"))
    }
}
