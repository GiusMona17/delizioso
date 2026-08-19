package com.delizioso.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.delizioso.app.data.local.ThemeMode
import com.delizioso.app.ui.DeliziosoApp
import com.delizioso.app.ui.theme.DeliziosoTheme

class MainActivity : ComponentActivity() {

    /** Link handed to us by the share sheet, consumed once the import starts. */
    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink = SharedLink.fromIntent(intent)
        enableEdgeToEdge()
        val preferences = (application as DeliziosoApplication).container.preferences
        setContent {
            // Collected at the root so the choice reaches every screen, and starts
            // from the system setting rather than flashing the wrong palette while
            // DataStore reads.
            val mode by preferences.themeMode.collectAsStateWithLifecycle(
                initialValue = ThemeMode.SYSTEM
            )
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DeliziosoTheme(darkTheme = dark) {
                DeliziosoApp(
                    sharedLink = sharedLink,
                    onSharedLinkHandled = { sharedLink = null },
                )
            }
        }
    }

    /** singleTask: a second share arrives here rather than in a new activity. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        SharedLink.fromIntent(intent)?.let { sharedLink = it }
    }
}
