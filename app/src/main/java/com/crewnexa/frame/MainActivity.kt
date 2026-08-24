package com.crewnexa.frame

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.crewnexa.frame.ui.BrowseScreen
import com.crewnexa.frame.ui.DisplayScreen
import com.crewnexa.frame.ui.PairingScreen

/**
 * A frame has exactly two modes and the remote moves between them.
 *
 * Display is the resting state and it is where the panel spends almost all of
 * its life. Browse only exists because someone occasionally wants to choose
 * something, and it is built D-pad first because that is the only input a TV
 * reliably has.
 *
 * Pairing is not a mode. It is what the panel shows when it has nothing else it
 * can honestly show.
 */
class MainActivity : ComponentActivity() {

    private val vm: FrameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by vm.state.collectAsState()
            when (state) {
                is FrameUiState.NeedsPairing -> PairingScreen(
                    pickerUri = (state as FrameUiState.NeedsPairing).pickerUri,
                    code = (state as FrameUiState.NeedsPairing).code,
                )
                is FrameUiState.Displaying -> DisplayScreen(
                    items = (state as FrameUiState.Displaying).items,
                    durationSeconds = (state as FrameUiState.Displaying).durationSeconds,
                    paused = (state as FrameUiState.Displaying).paused,
                )
                is FrameUiState.Browsing -> BrowseScreen(
                    rows = (state as FrameUiState.Browsing).rows,
                    onPlay = vm::playAlbum,
                )
            }
        }
    }
}
