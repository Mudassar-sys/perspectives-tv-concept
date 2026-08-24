package com.crewnexa.frame.remote

/**
 * Everything the phone can tell the frame to do. Kept deliberately small.
 * A remote that can do fifty things is a remote nobody learns.
 */
sealed interface RemoteCommand {
    data object Next : RemoteCommand
    data object Previous : RemoteCommand
    data class SetPaused(val paused: Boolean) : RemoteCommand
    data class SetDurationSeconds(val seconds: Int) : RemoteCommand
    data class PlayAlbum(val albumId: Long) : RemoteCommand
    data object RequestPickerSession : RemoteCommand
}

data class FrameState(
    val albumId: Long?,
    val itemIndex: Int,
    val paused: Boolean,
    val durationSeconds: Int,
    val online: Boolean,
    val cachedItems: Int,
)
