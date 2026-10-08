package com.delizioso.app.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class DocsStatusTest {

    @Test
    fun `off is said first`() {
        assertEquals(DocsStatus.Off, docsStatus(enabled = false, configured = true, lastPushedAt = 5, lastError = "x"))
    }

    @Test
    fun `without a server the owner is sent to set it`() {
        assertEquals(DocsStatus.NeedsServer, docsStatus(enabled = true, configured = false, lastPushedAt = null, lastError = null))
    }

    @Test
    fun `an error wins over a past delivery`() {
        assertEquals(DocsStatus.Failed("pi spento"), docsStatus(true, true, 5, "pi spento"))
    }

    @Test
    fun `a delivery is reported with its time`() {
        assertEquals(DocsStatus.Sent(5), docsStatus(true, true, 5, null))
    }

    @Test
    fun `nothing sent yet`() {
        assertEquals(DocsStatus.Never, docsStatus(true, true, null, null))
    }
}
