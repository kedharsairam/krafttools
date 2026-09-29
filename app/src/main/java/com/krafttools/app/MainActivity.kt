package com.krafttools.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.krafttools.app.ui.ToolboxNav
import com.krafttools.app.ui.theme.ToolboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ToolboxTheme {
                ToolboxNav()
            }
        }
    }

    // Volume keys count on the tally screen only: TallyScreen installs
    // its handler on entry and clears it on exit. Any other screen —
    // or no screen — falls through to normal volume behavior.
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        val handler = com.krafttools.app.ui.TallyVolumeKeys.onVolume
        if (handler != null &&
            (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
        ) {
            handler()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
