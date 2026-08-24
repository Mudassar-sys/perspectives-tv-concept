package com.crewnexa.frame.pairing

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PairingCodeServiceTest {

    @Test
    fun `code expires after the ttl`() {
        var now = 0L
        val svc = PairingCodeService(clock = { now }, random = Random(1))
        val challenge = svc.issue()

        now += PairingCodeService.TTL_MS + 1
        assertEquals(PairingCodeService.Result.Expired, svc.verify(challenge.code))
    }

    @Test
    fun `a code cannot be used twice`() {
        var now = 0L
        val svc = PairingCodeService(clock = { now }, random = Random(2))
        val challenge = svc.issue()

        assertEquals(PairingCodeService.Result.Ok, svc.verify(challenge.code))
        assertEquals(PairingCodeService.Result.Expired, svc.verify(challenge.code))
    }

    @Test
    fun `locks out after five wrong attempts`() {
        var now = 0L
        val svc = PairingCodeService(clock = { now }, random = Random(3))
        val challenge = svc.issue()
        val wrong = challenge.code.reversed().let { if (it == challenge.code) "000000" else it }

        repeat(PairingCodeService.MAX_FAILURES) {
            assertEquals(PairingCodeService.Result.Wrong, svc.verify(wrong))
        }
        // Even the correct code is refused once the frame has locked out.
        assertEquals(PairingCodeService.Result.LockedOut, svc.verify(challenge.code))
    }

    @Test
    fun `code is six digits`() {
        val svc = PairingCodeService(random = Random(4))
        val code = svc.issue().code
        assertEquals(PairingCodeService.CODE_LENGTH, code.length)
        assertEquals(true, code.all { it.isDigit() })
    }
}
