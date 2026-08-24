package com.crewnexa.frame.content

import java.io.File

/**
 * The published specification gives the panel 128 GB, and the product is sold on
 * the promise that the photos live on the frame rather than in someone's cloud.
 * Both of those statements are only true if the active album is actually held
 * on disk, so this is not an optimisation. It is the feature.
 *
 * The rule the eviction follows is simple: never drop something the frame is
 * about to show. A least-recently-used cache does the wrong thing for a
 * slideshow, because the item it evicts first is the one coming up next in the
 * loop. So the playing album is pinned and everything else competes for what
 * is left.
 */
class AlbumCache(
    private val root: File,
    private val budgetBytes: Long = 96L * 1024 * 1024 * 1024,
) {

    private var pinnedAlbumId: Long? = null

    fun pin(albumId: Long) { pinnedAlbumId = albumId }

    fun albumDir(albumId: Long): File =
        File(root, "album-$albumId").apply { mkdirs() }

    fun has(albumId: Long, itemId: String): Boolean =
        File(albumDir(albumId), itemId).exists()

    fun put(albumId: Long, itemId: String, bytes: ByteArray) {
        File(albumDir(albumId), itemId).writeBytes(bytes)
        trimToBudget()
    }

    fun usedBytes(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun trimToBudget() {
        var used = usedBytes()
        if (used <= budgetBytes) return

        val candidates = root.listFiles()
            ?.filter { it.isDirectory && it.name != "album-$pinnedAlbumId" }
            ?.sortedBy { it.lastModified() }
            ?: return

        for (dir in candidates) {
            if (used <= budgetBytes) break
            used -= dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            dir.deleteRecursively()
        }
    }
}
