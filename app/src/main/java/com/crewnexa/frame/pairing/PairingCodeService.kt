package com.crewnexa.frame.pairing

import kotlin.random.Random

/**
 * Once the frame has network, the phone still has to prove it is allowed to
 * control this particular frame. The published API has no concept of a device
 * at all today, so this is the piece that has to be added on both sides.
 *
 * The user-visible half is a six digit code on the panel. The code is short
 * because it is read off a wall from across a room, and short codes are
 * guessable, so the security does not rest on the code. It rests on three
 * things around it: the code is only valid for two minutes, only one is live at
 * a time, and the frame stops answering after five wrong attempts until someone
 * is physically present to trigger a new one.
 *
 * What the phone gets back is a device-scoped token. It must not be the user's
 * own session token. A frame hangs in a hallway, it can be lifted off the wall,
 * and its storage should never be worth stealing.
 */
class PairingCodeService(
    private val clock: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {

    data class Challenge(
        val code: String,
        val issuedAt: Long,
        val expiresAt: Long,
    )

    private var active: Challenge? = null
    private var failures = 0

    fun issue(): Challenge {
        val now = clock()
        val code = (0 until CODE_LENGTH)
            .map { random.nextInt(0, 10) }
            .joinToString("")
        return Challenge(code, now, now + TTL_MS).also {
            active = it
            failures = 0
        }
    }

    sealed interface Result {
        data object Ok : Result
        data object Expired : Result
        data object Wrong : Result
        data object LockedOut : Result
    }

    fun verify(candidate: String): Result {
        val challenge = active ?: return Result.Expired
        if (failures >= MAX_FAILURES) return Result.LockedOut
        if (clock() > challenge.expiresAt) {
            active = null
            return Result.Expired
        }
        // Constant time compare. A timing difference on a six digit code is
        // small but it is free to avoid.
        val ok = constantTimeEquals(challenge.code, candidate)
        if (!ok) {
            failures++
            return Result.Wrong
        }
        active = null
        return Result.Ok
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    companion object {
        const val CODE_LENGTH = 6
        const val TTL_MS = 2 * 60 * 1000L
        const val MAX_FAILURES = 5
    }
}
