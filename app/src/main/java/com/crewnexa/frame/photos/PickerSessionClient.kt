package com.crewnexa.frame.photos

import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Google Photos Picker API.
 *
 * Why this class exists at all:
 * on 31 March 2025 Google removed photoslibrary.readonly, photoslibrary.sharing
 * and the full photoslibrary scope. An app can no longer read a user's library.
 * The Picker API is the replacement, and it is deliberately interactive: the user
 * selects on their own device, every single time.
 *
 * That has one consequence that decides the whole architecture of a wall frame.
 * There is no browser and no Google Photos app on the panel, so the panel can
 * never do the picking. It can only create a session, put the resulting
 * pickerUri on screen as a QR code, and wait. The phone is not a convenience
 * feature here. It is the only way a photo can reach the wall.
 *
 * Sessions are single use. Once the user finishes, that pickerUri is dead and
 * the next selection needs a fresh session.
 */
class PickerSessionClient(
    private val http: OkHttpClient,
    private val accessTokenProvider: suspend () -> String,
) {

    data class Session(
        val id: String,
        val pickerUri: String,
        val pollInterval: Duration,
        val timeout: Duration,
    )

    data class PickedItem(
        val id: String,
        val baseUrl: String,
        val mimeType: String,
        val widthPx: Int,
        val heightPx: Int,
    ) {
        /**
         * Picker base URLs are not renderable as they come back. They need a size
         * suffix. We ask for the long edge the panel actually has rather than the
         * original, which on a modern phone is routinely 40 megapixels and would
         * stall a TV-class SoC on decode.
         */
        fun renderUrl(longEdgePx: Int): String = "$baseUrl=w$longEdgePx-h$longEdgePx"
    }

    suspend fun createSession(): Session {
        val body = "{}".toRequestBody(JSON)
        val req = Request.Builder()
            .url("$BASE/v1/sessions")
            .header("Authorization", "Bearer ${accessTokenProvider()}")
            .post(body)
            .build()

        http.newCall(req).execute().use { res ->
            require(res.isSuccessful) { "createSession failed: ${res.code}" }
            val json = JSONObject(res.body!!.string())
            return Session(
                id = json.getString("id"),
                pickerUri = json.getString("pickerUri"),
                pollInterval = json.optJSONObject("pollingConfig")
                    ?.optString("pollInterval")
                    .toDurationOrDefault(3.seconds),
                timeout = json.optJSONObject("pollingConfig")
                    ?.optString("timeoutIn")
                    .toDurationOrDefault(10.seconds * 60),
            )
        }
    }

    /**
     * Google tells us how often to poll in pollingConfig. Ignoring it and polling
     * on our own schedule is how an app gets rate limited, so we honour it and
     * back off gently if the session is slow to settle.
     */
    suspend fun awaitSelection(session: Session): Boolean {
        var waited = Duration.ZERO
        var interval = session.pollInterval

        while (waited < session.timeout) {
            delay(interval)
            waited += interval

            val req = Request.Builder()
                .url("$BASE/v1/sessions/${session.id}")
                .header("Authorization", "Bearer ${accessTokenProvider()}")
                .get()
                .build()

            http.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val json = JSONObject(res.body!!.string())
                    if (json.optBoolean("mediaItemsSet", false)) return true
                }
            }
            interval = (interval * 1.25).coerceAtMost(15.seconds)
        }
        return false
    }

    suspend fun listPicked(session: Session): List<PickedItem> {
        val out = mutableListOf<PickedItem>()
        var pageToken: String? = null

        do {
            val url = buildString {
                append("$BASE/v1/mediaItems?sessionId=${session.id}&pageSize=100")
                pageToken?.let { append("&pageToken=$it") }
            }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${accessTokenProvider()}")
                .get()
                .build()

            http.newCall(req).execute().use { res ->
                require(res.isSuccessful) { "listPicked failed: ${res.code}" }
                val json = JSONObject(res.body!!.string())
                val arr = json.optJSONArray("mediaItems") ?: return@use
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    val file = item.getJSONObject("mediaFile")
                    val meta = file.optJSONObject("mediaFileMetadata")
                    out += PickedItem(
                        id = item.getString("id"),
                        baseUrl = file.getString("baseUrl"),
                        mimeType = file.optString("mimeType", "image/jpeg"),
                        widthPx = meta?.optInt("width") ?: 0,
                        heightPx = meta?.optInt("height") ?: 0,
                    )
                }
                pageToken = json.optString("nextPageToken").ifEmpty { null }
            }
        } while (pageToken != null)

        return out
    }

    private fun String?.toDurationOrDefault(fallback: Duration): Duration {
        // Google returns protobuf durations as "3.5s"
        val secs = this?.removeSuffix("s")?.toDoubleOrNull() ?: return fallback
        return (secs * 1000).toLong().milliseconds
    }

    companion object {
        private const val BASE = "https://photospicker.googleapis.com"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The only Photos scope that still gives an app anything useful. */
        const val SCOPE = "https://www.googleapis.com/auth/photospicker.mediaitems.readonly"
    }
}
