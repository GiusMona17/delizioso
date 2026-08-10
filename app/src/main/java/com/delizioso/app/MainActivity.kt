package com.delizioso.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.delizioso.app.ui.DeliziosoApp
import com.delizioso.app.ui.theme.DeliziosoTheme

class MainActivity : ComponentActivity() {

    /** Link handed to us by the share sheet, consumed once the import starts. */
    private var sharedLink by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedLink = SharedLink.fromIntent(intent)
        enableEdgeToEdge()
        setContent {
            DeliziosoTheme {
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
