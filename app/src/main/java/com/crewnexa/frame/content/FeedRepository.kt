package com.crewnexa.frame.content

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * The gallery web app builds its home screen by calling one endpoint per
 * category. Measured against the live API on 24 August 2026 that is 28 requests
 * fired together, and under that load each one comes back in 873 to 1168 ms.
 * Called on its own the same endpoint answers in 297 to 649 ms, so the delay is
 * contention, not the database. The responses carry no cache-control header and
 * sit behind no CDN.
 *
 * A browser survives that. A TV does not. The panel is a weaker SoC, the user is
 * holding a remote, and a spinner on a wall reads as a broken product rather
 * than a slow one.
 *
 * So the frame asks for one aggregated document instead, and falls back to the
 * per-category calls only when the server has not been updated yet. That
 * fallback is what makes this shippable before any backend work lands.
 */
class FeedRepository(
    private val http: OkHttpClient,
    private val baseUrl: String,
) {

    data class Row(val title: String, val itemIds: List<Long>)
    data class Feed(val rows: List<Row>, val fromCache: Boolean)

    suspend fun home(): Feed = runCatching { aggregated() }.getOrElse { perCategory() }

    /** One call. What the TV should be asking for. */
    private fun aggregated(): Feed {
        val req = Request.Builder()
            .url("$baseUrl/api/v1/getHomeFeed?rows=8&itemsPerRow=15")
            .header("Accept", "application/json")
            .build()

        http.newCall(req).execute().use { res ->
            require(res.isSuccessful) { "no aggregated feed endpoint" }
            val data = JSONObject(res.body!!.string()).getJSONObject("data")
            val arr = data.getJSONArray("rows")
            val rows = (0 until arr.length()).map { i ->
                val row = arr.getJSONObject(i)
                val ids = row.getJSONArray("itemIds")
                Row(
                    title = row.getString("title"),
                    itemIds = (0 until ids.length()).map { ids.getLong(it) },
                )
            }
            return Feed(rows, fromCache = res.cacheResponse != null)
        }
    }

    /**
     * The path that exists today. Kept so the app runs against the current
     * server, and capped at eight rows because the panel only ever shows the
     * first screen before the user moves.
     *
     * Note the limit parameter is sent but not honoured by the server. Asking
     * for 200 trending artists returns 15. Code that assumes otherwise will
     * quietly show a short list forever.
     */
    private suspend fun perCategory(): Feed = coroutineScope {
        val categories = categories().take(MAX_ROWS)
        val rows = categories.map { name ->
            async {
                val url = "$baseUrl/api/v1/getArtByCategory" +
                    "?category=${java.net.URLEncoder.encode(name, "UTF-8")}&limit=15&page=0"
                val req = Request.Builder().url(url).build()
                http.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@async Row(name, emptyList())
                    val content = JSONObject(res.body!!.string())
                        .getJSONObject("data")
                        .getJSONArray("content")
                    Row(name, (0 until content.length()).map { content.getJSONObject(it).getLong("id") })
                }
            }
        }.map { it.await() }
        Feed(rows.filter { it.itemIds.isNotEmpty() }, fromCache = false)
    }

    private fun categories(): List<String> {
        val req = Request.Builder().url("$baseUrl/api/v1/getCategoryList").build()
        http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyList()
            val arr = JSONObject(res.body!!.string()).getJSONArray("data")
            return (0 until arr.length()).map { arr.getString(it) }
        }
    }

    companion object {
        /** Eight rows is what fits above the fold on a 3:2 panel at 4K. */
        const val MAX_ROWS = 8
    }
}
