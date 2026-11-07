package com.aniq.glassreader

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.aniq.glassreader.data.Store
import com.aniq.glassreader.ui.App
import com.aniq.glassreader.ui.NavRequest
import com.aniq.glassreader.ui.ReaderKeys
import com.aniq.glassreader.widget.Widgets

class MainActivity : ComponentActivity() {

    private val navRequest = mutableStateOf<NavRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        window.isNavigationBarContrastEnforced = false
        requestHighestRefreshRate()
        handle(intent)
        setContent { App(navRequest) }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (ReaderKeys.active && Store.settings.value.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            ReaderKeys.turns.tryEmit(if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (ReaderKeys.active && Store.settings.value.volumeKeys && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Folding/unfolding switches displays; re-pick the fastest mode for the new panel.
        requestHighestRefreshRate()
    }

    /** Ask for the panel's highest refresh rate (120 Hz on the Fold) at the current resolution. */
    private fun requestHighestRefreshRate() {
        val d = display ?: return
        val cur = d.mode
        val best = d.supportedModes
            .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        val lp = window.attributes
        if (lp.preferredDisplayModeId != best.modeId) {
            lp.preferredDisplayModeId = best.modeId
            window.attributes = lp
        }
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        when {
            intent.action == Intent.ACTION_VIEW && intent.data != null ->
                navRequest.value = NavRequest.Import(intent.data as Uri)
            intent.hasExtra(Widgets.EXTRA_OPEN_BOOK) -> navRequest.value = NavRequest.OpenBook(
                intent.getStringExtra(Widgets.EXTRA_OPEN_BOOK)!!,
                intent.getIntExtra(Widgets.EXTRA_OPEN_PARA, -1).takeIf { it >= 0 },
            )
            intent.getBooleanExtra(Widgets.EXTRA_OPEN_HIGHLIGHTS, false) -> navRequest.value = NavRequest.Highlights
        }
        intent.replaceExtras(Bundle())
    }
}
