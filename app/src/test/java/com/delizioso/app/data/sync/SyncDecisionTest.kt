package com.delizioso.app.data.sync

import com.delizioso.app.data.sync.SyncAction.CONFLICT
import com.delizioso.app.data.sync.SyncAction.NOTHING
import com.delizioso.app.data.sync.SyncAction.PULL
import com.delizioso.app.data.sync.SyncAction.PUSH
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncDecisionTest {

    private data class Case(
        val why: String,
        val server: String?,
        val base: String?,
        val changed: Boolean,
        val empty: Boolean,
        val expected: SyncAction,
    )

    @Test
    fun `every row of the spec's decision table`() {
        val cases = listOf(
            Case("nothing anywhere", null, null, changed = false, empty = true, expected = NOTHING),
            Case("first device", null, null, changed = true, empty = false, expected = PUSH),
            Case("Pi lost its data", null, "\"3-a\"", changed = false, empty = false, expected = PUSH),
            Case("local edit", "e1", "e1", changed = true, empty = false, expected = PUSH),
            Case("user deleted everything", "e1", "e1", changed = true, empty = true, expected = PUSH),
            Case("in sync", "e1", "e1", changed = false, empty = false, expected = NOTHING),
            Case("other device pushed", "e2", "e1", changed = false, empty = false, expected = PULL),
            Case("fresh install", "e2", null, changed = true, empty = true, expected = PULL),
            Case("empty device, old base", "e2", "e1", changed = true, empty = true, expected = PULL),
            Case("both changed", "e2", "e1", changed = true, empty = false, expected = CONFLICT),
            Case("own data, never synced", "e2", null, changed = true, empty = false, expected = CONFLICT),
        )
        for (c in cases) {
            assertEquals(c.why, c.expected, decide(c.server, c.base, c.changed, c.empty))
        }
    }
}
