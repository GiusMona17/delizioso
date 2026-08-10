package com.delizioso.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.delizioso.app.ui.DeliziosoApp
import com.delizioso.app.ui.theme.DeliziosoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DeliziosoTheme {
                DeliziosoApp()
            }
        }
    }
}
