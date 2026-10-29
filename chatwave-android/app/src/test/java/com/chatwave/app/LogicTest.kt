package com.chatwave.app

import com.chatwave.app.core.Validation
import com.chatwave.app.feature.chat.SendState
import com.chatwave.app.feature.chat.UiMessage
import com.chatwave.app.feature.chat.merge
import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    @Test fun usernameRules() {
        assertNull(Validation.username("Sharan_01"))
        assertNotNull(Validation.username("a!"))
        assertNotNull(Validation.username("ab"))
    }
    @Test fun passwordRules() { assertNotNull(Validation.password("short")); assertNull(Validation.password("long-enough")) }

    @Test fun mergeDedupesByClientId() {
        val pending = UiMessage("c1", null, null, "hi", true, SendState.SENDING, null)
        val stored = UiMessage("c1", "s1", 10, "hi", true, SendState.SENT, "t")
        val out = listOf(pending).merge(stored).merge(stored)
        assertEquals(1, out.size); assertEquals("s1", out[0].serverId)
    }
    @Test fun mergeOrdersBySeqWithPendingLast() {
        val a = UiMessage("a", "1", 2, "a", false, SendState.SENT, "t")
        val b = UiMessage("b", "2", 1, "b", false, SendState.SENT, "t")
        val p = UiMessage("p", null, null, "p", true, SendState.SENDING, null)
        assertEquals(listOf("b", "a", "p"), listOf(a).merge(p).merge(b).map { it.clientId })
    }
}
